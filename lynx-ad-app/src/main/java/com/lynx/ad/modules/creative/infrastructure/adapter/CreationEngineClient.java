package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Calls LynxAd-ai-engine capability APIs, rather than individual model providers. */
@Component
public class CreationEngineClient {
    private final LynxAdProperties properties;
    private final ObjectMapper json;
    private final String apiKey;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public CreationEngineClient(LynxAdProperties properties,ObjectMapper json,
            @Value("${lynx.ad.monitoring.engine-api-key:}") String apiKey){this.properties=properties;this.json=json;this.apiKey=apiKey;}
    public JsonNode post(String path,Object payload){
        if(payload instanceof java.util.Map<?,?> body && (!body.containsKey("store_id") || body.get("store_id")==null || body.get("store_id").toString().isBlank()))throw new BizException(8002,"应用调用引擎必须指定真实租户");
        return exchange(path,payload);
    }
    public JsonNode get(String path){return exchange(path,null);}
    public JsonNode find(String path){return exchange(path,null,true);}
    public String base(){String base=properties.getAi().getReview().getBaseUrl();if(base==null||base.isBlank())throw new BizException(8001,"LynxAd-ai-engine 地址未配置");return base.replaceAll("/+$","");}
    private JsonNode exchange(String path,Object payload){
        return exchange(path,payload,false);
    }
    private JsonNode exchange(String path,Object payload,boolean missingAllowed){
        try {
            var req=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(240)).header("Accept","application/json").header("X-LynxAd-Caller","lynxad-app");
            if(apiKey!=null&&!apiKey.isBlank())req.header("X-API-Key",apiKey);
            if(payload==null)req.GET();else req.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload),StandardCharsets.UTF_8));
            var response=http.send(req.build(),HttpResponse.BodyHandlers.ofByteArray());
            if(missingAllowed&&response.statusCode()==404)return null;
            if(response.statusCode()<200||response.statusCode()>=300) throw new BizException(payload!=null&&response.statusCode()>=500?8003:8002,"AI 引擎请求异常（HTTP "+response.statusCode()+"），请检查任务状态与引擎配置");
            if(response.body().length>8*1024*1024)throw new BizException(8002,"AI 引擎响应超出限制");
            return json.readTree(response.body());
        }catch(BizException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new BizException(payload==null?8002:8003,"AI 引擎请求中断，提交结果待确认");}
        catch(Exception e){throw new BizException(payload==null?8002:8003,"AI 引擎连接失败或响应超时；已提交任务时请先查询状态");}
    }
    public static String query(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
