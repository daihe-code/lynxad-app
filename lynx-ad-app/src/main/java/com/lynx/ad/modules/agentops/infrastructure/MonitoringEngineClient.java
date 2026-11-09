package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MonitoringEngineClient {
    private final LynxAdProperties properties;
    private final ObjectMapper json;
    private final String key,apiKey;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build();
    public MonitoringEngineClient(LynxAdProperties properties,ObjectMapper json,
            @Value("${lynx.ad.monitoring.api-key:}") String key,@Value("${lynx.ad.monitoring.engine-api-key:}") String apiKey) {
        this.properties=properties;this.json=json;this.key=key;this.apiKey=apiKey;
    }
    public boolean ready() {return !key.isBlank() && properties.getAi().getReview().getBaseUrl()!=null && !properties.getAi().getReview().getBaseUrl().isBlank();}
    public JsonNode creative(String tenant,String path,Object body){
        return creative(tenant,path,body,null);
    }
    public JsonNode creative(String tenant,String path,Object body,String executionTicket){
        if(!ready())throw new BizException(1801,"盯盘服务尚未配置");
        try{
            var uri=properties.getAi().getReview().getBaseUrl().replaceAll("/+$","")+"/api/v1/monitoring/external/creative-jobs"+path;
            var request=HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("X-Monitoring-Key",key).header("X-Store-ID",tenant).header("X-LynxAd-Caller","lynxad-app");
            if(!apiKey.isBlank())request.header("X-API-Key",apiKey);
            if(executionTicket!=null)request.header("X-Workspace-Execution",executionTicket);
            if(body==null)request.GET();else request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404&&body==null)return null;
            if(response.statusCode()<200||response.statusCode()>=300)throw new BizException(response.statusCode()<500?8002:8003,"生成提交或查询失败（HTTP "+response.statusCode()+"），先回查原请求");
            if(response.body().length()>1024*1024)throw new BizException(1801,"生成响应超出限制");
            return json.readTree(response.body());
        }catch(BizException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new BizException(8003,"生成请求中断，先回查原请求");}catch(Exception e){throw new BizException(8003,"生成请求结果待确认，先回查原请求");}
    }
    public JsonNode submit(String tenant,String payload) {
        if(!ready())throw new BizException(1801,"盯盘服务尚未就绪，请联系管理员完成服务配置");
        try {
            var body=json.readTree(payload);
            if(!tenant.equals(body.path("store_id").asText()))throw new BizException(1802,"盯盘数据租户不匹配");
            String base=properties.getAi().getReview().getBaseUrl().replaceAll("/+$","");
            var request=HttpRequest.newBuilder(URI.create(base+"/api/v1/monitoring/external/observations:batch"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").header("Accept","application/json")
                .header("X-Monitoring-Key",key).header("X-Store-ID",tenant).header("X-LynxAd-Caller","lynxad-app");
            if(!apiKey.isBlank())request.header("X-API-Key",apiKey);
            var response=http.send(request.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new BizException(1801,"盯盘引擎请求失败（HTTP "+response.statusCode()+"），数据会自动重试");
            if(response.body().length()>1024*1024)throw new BizException(1801,"盯盘引擎响应超出限制");
            var result=json.readTree(response.body());
            if(result==null || !body.path("request_id").asText().equals(result.path("request_id").asText()) ||
                    !tenant.equals(result.path("store_id").asText()) || !body.path("account_id").asText().equals(result.path("account_id").asText()) ||
                    !body.path("plan_id").asText().equals(result.path("plan_id").asText()) ||
                    !body.path("logical_plan_id").asText().equals(result.path("logical_plan_id").asText()) || !"CNY".equals(result.path("currency").asText()) ||
                    body.path("policy_version").asLong()!=result.path("policy_version").asLong() ||
                    !"qianchuan_live".equals(result.path("source").asText()) || !body.path("policy").path("mode").asText().equals(result.path("mode").asText()) ||
                    !result.path("execution_allowed").isBoolean() || result.path("execution_allowed").asBoolean()!= "AUTO_LIMITED".equals(body.path("policy").path("mode").asText()) ||
                    !result.path("reasons").isArray() || !result.path("recommendations").isArray() || !result.hasNonNull("state") || !result.hasNonNull("observed_at"))
                throw new BizException(1801,"盯盘引擎响应绑定不一致");
            if(!java.time.OffsetDateTime.parse(body.path("observed_at").asText()).toInstant().equals(java.time.OffsetDateTime.parse(result.path("observed_at").asText()).toInstant()))throw new BizException(1801,"盯盘引擎响应时间不一致");
            return result;
        } catch(BizException ex){throw ex;}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new BizException(1801,"盯盘请求中断，待自动重试");}
        catch(Exception ex){throw new BizException(1801,"暂无法连接盯盘引擎，数据已保留并将自动重试");}
    }
}
