package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.AssetReviewPort;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiEngineAssetReviewAdapter implements AssetReviewPort {
    private final LynxAdProperties properties;
    private final ObjectMapper mapper;
    // Uvicorn serves HTTP/1.1; h2c upgrade negotiation can lose request bodies.
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public AiEngineAssetReviewAdapter(LynxAdProperties properties, ObjectMapper mapper) { this.properties=properties; this.mapper=mapper; }
    @Override public Result submit(AssetKind kind, String url, String text, String tenant, String asset) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("platform","douyin"); body.put("store_id",tenant);
        body.put("context",Map.of("asset_id",asset,"source","local_upload"));
        if(kind==AssetKind.TEXT) {
            if(text==null || text.isBlank() || text.length()>50000) throw new BizException(1525,"文本审核支持 1–50000 个字符，请拆分文件后重新上传");
            body.put("content_text",text);
        } else body.put("material_url",absoluteMediaUrl(url));
        return parse(send(kind,null,body),kind,null);
    }
    @Override public Result poll(AssetKind kind,String id) {
        if(!Set.of(AssetKind.VIDEO,AssetKind.AUDIO).contains(kind) || id==null || !id.matches("[A-Za-z0-9_-]{1,128}"))
            throw new BizException(1525,"AI 审核任务标识不合法");
        return parse(send(kind,id,null),kind,id);
    }
    private String absoluteMediaUrl(String value) {
        if(value==null || value.isBlank()) throw new BizException(1525,"审核素材访问地址缺失");
        URI uri=URI.create(value);
        if(!uri.isAbsolute()) {
            String base=properties.getAi().getReview().getMediaBaseUrl();
            if(base==null || base.isBlank()) throw new BizException(1525,"请配置 AI 引擎可访问的平台媒体地址");
            uri=URI.create(base.replaceAll("/+$","")+"/"+value.replaceAll("^/+",""));
        }
        if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
            throw new BizException(1525,"审核素材地址必须使用 HTTP 或 HTTPS");
        return uri.toString();
    }
    private JsonNode send(AssetKind kind,String id,Map<String,Object> body) {
        var config=properties.getAi().getReview();
        String base=config.getBaseUrl();
        if(base==null || base.isBlank()) throw new BizException(1525,"AI 审核引擎地址尚未配置");
        try {
            URI endpoint=URI.create(base.replaceAll("/+$","")+"/api/v1/capabilities/review/"+kind.name().toLowerCase(Locale.ROOT)+(id==null ? "" : "/"+id));
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofMillis(Math.max(1000,Math.min(config.getTimeoutMs(),300000))))
                    .header("Accept","application/json");
            if(body==null) request.GET();
            else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),StandardCharsets.UTF_8));
            var pending=client.sendAsync(request.build(),HttpResponse.BodyHandlers.ofByteArray());
            HttpResponse<byte[]> response;
            try { response=pending.get(Math.max(1000,Math.min(config.getTimeoutMs(),300000)),java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch(Exception ex) { pending.cancel(true); throw ex; }
            if(response.statusCode()<200 || response.statusCode()>=300) throw new BizException(1525,"AI 审核接口暂时不可用（HTTP "+response.statusCode()+"），请稍后重试");
            if(response.body().length>1024*1024) throw new BizException(1525,"AI 审核响应异常，请稍后重试");
            return mapper.readTree(response.body());
        } catch(BizException ex) { throw ex; }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new BizException(1525,"AI 审核请求中断，请重新查询或重试"); }
        catch(Exception ex) { throw new BizException(1525,"AI 审核引擎连接失败或响应超时，请检查引擎服务后重试"); }
    }
    private Result parse(JsonNode raw,AssetKind kind,String expectedId) {
        if(raw==null || !raw.isObject() || !kind.name().equalsIgnoreCase(raw.path("material_type").asText()))
            throw new BizException(1525,"AI 审核响应类型不匹配");
        String status=raw.path("status").asText(),id=raw.path("review_id").asText(null);
        if(expectedId!=null && !expectedId.equals(id)) throw new BizException(1525,"AI 审核响应任务不匹配");
        if(expectedId==null && Set.of(AssetKind.VIDEO,AssetKind.AUDIO).contains(kind)) {
            if(id==null || !id.matches("[A-Za-z0-9_-]{1,128}") || !Set.of("queued","running","processing","succeeded","failed").contains(status))
                throw new BizException(1525,"AI 审核创建任务未返回有效任务标识");
            // Persist the task first. Even a rapidly completed task is finalized through GET polling.
            return new Result("PENDING",id,null,null,List.of(),null);
        }
        if(Set.of("queued","running","processing").contains(status)) {
            if(!Set.of(AssetKind.VIDEO,AssetKind.AUDIO).contains(kind) || id==null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw new BizException(1525,"AI 审核任务响应异常");
            return new Result("PENDING",id,null,null,List.of(),null);
        }
        if("failed".equals(status)) return new Result("FAILED",id,null,null,List.of(),safe(raw.path("error").path("message").asText("AI 审核处理失败，请检查引擎后重试")));
        JsonNode result=raw.hasNonNull("result") ? raw.path("result") : raw;
        if(!"succeeded".equals(status) || !"succeeded".equals(result.path("status").asText())
                || !kind.name().equalsIgnoreCase(result.path("material_type").asText())) throw new BizException(1525,"AI 审核没有返回有效结论，请重试");
        String decision=result.path("decision").asText();
        String mapped=switch(decision) { case "approved" -> result.path("passed").isBoolean() && result.path("passed").asBoolean() ? "APPROVED" : "FAILED"; case "rejected" -> "REJECTED"; case "manual_review" -> "MANUAL_REVIEW"; default -> "FAILED"; };
        List<String> reasons=new ArrayList<>();
        for(var reason:result.path("reasons")) {
            String message=reason.isTextual() ? reason.asText() : reason.path("message").asText(reason.path("reason").asText(reason.path("description").asText("")));
            if(!message.isBlank() && reasons.size()<20) reasons.add(safe(message));
        }
        return new Result(mapped,id,decision,safe(result.path("summary").asText("")),reasons,"FAILED".equals(mapped) ? "AI 审核结论不完整，未放行千川同步" : null);
    }
    private static String safe(String text) {
        String redacted=text.replaceAll("https?://\\S+","[素材地址]");
        return redacted.substring(0,Math.min(500,redacted.length()));
    }
}
