package com.lynx.ad.modules.identity.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.identity.domain.model.AwemeType;
import com.lynx.ad.modules.identity.domain.port.OauthTokenPort;
import com.lynx.ad.modules.identity.domain.service.AwemeCatalog;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnExpression("'${lynx.ad.oauth.qianchuan.mode:legacy}' == 'live' || '${lynx.ad.mock.qianchuan:true}' == 'false'")
public class LiveOauthTokenAdapter implements OauthTokenPort {
    private final QianchuanRestClient client;
    private final LynxAdProperties properties;
    private final OceanJson ocean;
    private final ObjectMapper json = new ObjectMapper();
    private final AwemeCatalog catalog = new AwemeCatalog();
    public record AuthorizedAdvertiser(String advertiserId,String accountName,String accountRole,String parentAccountId) {}
    public record TokenBundle(String accessToken,String refreshToken,Instant accessExpiresAt,Instant refreshExpiresAt) {
        @Override public String toString() { return "OAuth tokens [redacted]"; }
    }
    public static class RemoteFailure extends BizException {
        private final boolean permanent;
        public RemoteFailure(int code,String message,boolean permanent) { super(code,message); this.permanent=permanent; }
        public boolean permanent() { return permanent; }
    }
    public LiveOauthTokenAdapter(QianchuanRestClient client,LynxAdProperties properties,OceanJson ocean) {
        this.client=client; this.properties=properties; this.ocean=ocean;
    }
    public TokenBundle exchangeToken(String appId,String secret,String code) {
        return token(properties.getQianchuan().getOauth().getAccessTokenUrl(),Map.of("app_id",appId,"secret",secret,"auth_code",code),true);
    }
    public TokenBundle refreshToken(String appId,String secret,String refresh) {
        return token(properties.getQianchuan().getOauth().getRefreshTokenUrl(),Map.of("app_id",appId,"secret",secret,"grant_type","refresh_token","refresh_token",refresh),false);
    }
    private TokenBundle token(String url,Map<String,String> request,boolean exchange) {
        JsonNode root;
        try { root=ocean.root(client.exchange(RequestSpec.post(url).json(serialize(request)).build().withoutReplay(),String.class)); }
        catch(BizException ex) {
            boolean permanent=!exchange && (ex.code()==401 || ex.code()==40102 || ex.code()==40103 || ex.code()==40105);
            throw new RemoteFailure(permanent?1211:1205,(exchange?"千川授权换票失败":"千川令牌刷新失败")+"（平台错误码："+ex.code()+"）",permanent);
        }
        int code=root.path("code").asInt(-1);
        if(code!=0) {
            String msg=root.path("message").asText("").toLowerCase();
            boolean permanent=!exchange && (code==40102 || code==40103 || code==40105 || msg.contains("refresh_token expired") || msg.contains("refresh token expired") || msg.contains("invalid refresh") || msg.contains("已撤销") || msg.contains("刷新令牌已过期"));
            throw new RemoteFailure(permanent?1211:1205,(exchange?"千川授权换票失败":"千川令牌刷新失败")+"（平台错误码："+code+"）",permanent);
        }
        JsonNode data=root.path("data"); String access=data.path("access_token").asText(""); long ttl=data.path("expires_in").asLong(-1);
        String refresh=data.path("refresh_token").asText("");
        if(access.isBlank() || ttl<=0 || (exchange && refresh.isBlank())) throw new BizException(1205,"千川返回的授权凭证不完整");
        long refreshTtl=data.path("refresh_token_expires_in").asLong(-1); Instant now=Instant.now();
        return new TokenBundle(access,refresh.isBlank()?null:refresh,now.plusSeconds(ttl),refreshTtl<0?null:now.plusSeconds(refreshTtl));
    }
    @Override public OauthTokenResult exchange(String code) {
        assertConfigured(); var qc=properties.getOauth().getQianchuan(); var bundle=exchangeToken(qc.getAppId(),qc.getAppSecret(),code);
        var accounts=discover(bundle.accessToken());
        if(accounts.isEmpty()) throw new BizException(1205,"未找到已授权的巨量千川广告账户");
        var a=accounts.getFirst(); return result(bundle,a.advertiserId(),a.accountName(),loadAwemes(bundle.accessToken(),a.advertiserId()));
    }
    @Override public OauthTokenResult refresh(String refresh) {
        assertConfigured(); var qc=properties.getOauth().getQianchuan();
        return result(refreshToken(qc.getAppId(),qc.getAppSecret(),refresh),"","",List.of());
    }
    private OauthTokenResult result(TokenBundle b,String id,String name,List<AwemeDraft> awemes) {
        return new OauthTokenResult(id,name,b.accessToken(),b.refreshToken(),b.accessExpiresAt(),b.refreshExpiresAt(),awemes);
    }
    public List<AuthorizedAdvertiser> discover(String access) {
        Map<String,AuthorizedAdvertiser> out=new LinkedHashMap<>();
        JsonNode data=get(properties.getQianchuan().getOauth().getAdvertiserGetUrl(),access,Map.of());
        for(JsonNode a:list(data)) {
            if(a.has("is_valid") && !a.path("is_valid").asBoolean()) continue;
            String role=a.path("account_role").asText("ADVERTISER"), id=a.path("account_id").asText(a.path("advertiser_id").asText(""));
            switch(role) {
                case "ADVERTISER" -> add(out,a,role,null,true);
                case "PLATFORM_ROLE_SHOP_ACCOUNT" -> expand(out,access,properties.getQianchuan().getOauth().getShopAdvertiserListUrl(),Map.of("shop_id",id),role,id,false);
                case "PLATFORM_ROLE_ENTERPRISE_BP_ADMIN" -> expand(out,access,properties.getQianchuan().getOauth().getEbpAdvertiserListUrl(),Map.of("enterprise_organization_id",id,"account_source","QIANCHUAN"),role,id,true);
                case "CUSTOMER_ADMIN","PLATFORM_ROLE_CUSTOMER_ADMIN" -> expand(out,access,properties.getQianchuan().getOauth().getCustomerAdvertiserListUrl(),Map.of("cc_account_id",id,"account_source","QIANCHUAN"),role,id,true);
                default -> { }
            }
        }
        return List.copyOf(out.values());
    }
    private void expand(Map<String,AuthorizedAdvertiser> out,String access,String url,Map<String,String> params,String role,String parent,boolean filter) {
        for(int page=1;page<=1000;page++) {
            Map<String,String> query=new LinkedHashMap<>(params); query.put("page",String.valueOf(page)); query.put("page_size","100");
            JsonNode data=get(url,access,query), entries=list(data);
            for(JsonNode a:entries) add(out,a,role,parent,filter);
            int total=data.path("page_info").path("total_page").asInt(-1);
            if(entries.isEmpty() || (total>=0 && page>=total) || (total<0 && entries.size()<100)) return;
        }
        throw new BizException(1205,"授权账户数量超出同步范围，请缩小授权范围");
    }
    private void add(Map<String,AuthorizedAdvertiser> out,JsonNode a,String role,String parent,boolean filter) {
        String type=a.path("account_type").asText("");
        if(filter && !type.isBlank() && !"QIANCHUAN".equals(type) && !"ADVERTISER".equals(type)) return;
        String id=a.path("advertiser_id").asText(a.path("account_id").asText(""));
        if(!id.matches("[0-9]+")) return;
        String name=a.path("advertiser_name").asText(a.path("account_name").asText("千川账户 "+id));
        out.putIfAbsent(id,new AuthorizedAdvertiser(id,name,role,parent));
    }
    public List<AwemeDraft> loadAwemes(String access,String advertiser) {
        JsonNode data=get(properties.getQianchuan().getOauth().getAwemeAuthorizedUrl(),access,Map.of("advertiser_id",advertiser));
        Map<String,AwemeDraft> out=new LinkedHashMap<>();
        for(JsonNode a:list(data)) {
            String id=a.path("aweme_id").asText(""); if(id.isBlank()) continue;
            String name=a.path("aweme_name").asText(a.path("name").asText("抖音号 "+id));
            String avatar=a.path("aweme_avatar").asText(a.path("avatar").asText(""));
            AwemeType type; try { type=AwemeType.valueOf(a.path("aweme_type").asText("AUTHORIZED")); } catch(IllegalArgumentException ex) { type=AwemeType.AUTHORIZED; }
            List<String> authTypes=new ArrayList<>();
            if(a.path("auth_type").isArray()) for(JsonNode auth:a.path("auth_type")) {
                String value=auth.asText("");
                if(value.matches("[A-Z_]{1,40}") && !authTypes.contains(value)) authTypes.add(value);
            }
            // Preserve all returned relationships; a single legacy aweme_type loses multi-role labels.
            Boolean mall=a.path("is_allow_mall").isBoolean()?a.path("is_allow_mall").booleanValue():null;
            out.putIfAbsent(id,new AwemeDraft(id,name,avatar.isBlank()?null:avatar,type,a.path("is_primary").asBoolean(false),catalog.sceneHint(type),authTypes,mall));
        }
        return List.copyOf(out.values());
    }
    private JsonNode list(JsonNode data) {
        if(data.isArray()) return data;
        for(String name:List.of("list","account_list","advertiser_list","aweme_id_list")) if(data.path(name).isArray()) return data.path(name);
        return json.createArrayNode();
    }
    private JsonNode get(String url,String token,Map<String,String> params) {
        String query=params.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
        return ocean.dataOrThrow(client.exchange(RequestSpec.get(url+(query.isEmpty()?"":"?"+query)).header("Access-Token",token).build().withoutReplay(),String.class),1205,"千川账户信息同步失败");
    }
    private String serialize(Object value) { try { return json.writeValueAsString(value); } catch(Exception ex) { throw new BizException(1205,"授权请求构建失败"); } }
    private static String enc(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    private void assertConfigured() { if(!properties.isQianchuanLiveConfigured()) throw new BizException(1205,"千川授权未配置"); }
}
