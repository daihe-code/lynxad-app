package com.lynx.ad.infrastructure.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.identity.infrastructure.adapter.LiveOauthTokenAdapter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class QianchuanAwemeLabelsContractTest {
    private final ObjectMapper json=new ObjectMapper();
    private final LynxAdProperties properties=new LynxAdProperties();
    private final QianchuanRestClient client=mock(QianchuanRestClient.class);
    private final LiveOauthTokenAdapter adapter=new LiveOauthTokenAdapter(client,properties,new OceanJson(json));

    @Test void combinesProductMallCapabilityAndAllianceIdentityWithoutChangingAuthorization() throws Exception {
        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec request=call.getArgument(0); var query=query(request);
            assertEquals("123",query.get("advertiser_id"));
            assertEquals("access",request.headers().get("Access-Token"));
            assertNull(request.replayAccountId());
            if(relations(request)) {
                assertEquals(30,json.readTree(query.get("page_params")).path("page_size").asInt());
                return response("""
                    {"bind_aweme_user_infos":[
                      {"aweme_user_info":{"aweme_id":77,"aweme_name":"other"},
                       "alliance_info":{"is_alliance_talent":true,"allow_alliance_talent_auto_auth":false}},
                      {"aweme_user_info":{"aweme_id":999},"alliance_info":{"is_alliance_talent":true}}]}
                    """);
            }
            assertEquals("100",query.get("page_size"));
            if(query.containsKey("filtering")) {
                assertEquals(json.readTree("{\"marketing_goal\":\"VIDEO_PROM_GOODS\",\"scene\":\"CREATE\"}"),json.readTree(query.get("filtering")));
                return response("{\"aweme_id_list\":[{\"aweme_id\":77,\"is_allow_mall\":true},{\"aweme_id\":999,\"is_allow_mall\":true}]}");
            }
            return response("{\"aweme_id_list\":[{\"aweme_id\":77,\"aweme_name\":\"shop\",\"aweme_avatar\":\"https://example.com/avatar\",\"auth_type\":[\"OFFICIAL\"],\"is_allow_mall\":null}]}");
        });
        var accounts=adapter.loadAwemes("access","123");
        assertEquals(1,accounts.size()); var a=accounts.getFirst();
        assertEquals(List.of("OFFICIAL","ALLIANCE"),a.authTypes()); assertEquals(Boolean.TRUE,a.isAllowMall());
        assertEquals("shop",a.name()); assertEquals("https://example.com/avatar",a.avatar());
        assertEquals("AUTHORIZED",a.type().name()); assertFalse(a.primary());
    }

    @Test void falseMissingAndTextFlagsDoNotCreateCapabilityOrAllianceLabels() {
        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec request=call.getArgument(0);
            if(relations(request)) return response("""
                {"bind_aweme_user_infos":[
                  {"aweme_user_info":{"aweme_id":77},"alliance_info":{"is_alliance_talent":false,"allow_alliance_talent_auto_auth":true}},
                  {"aweme_user_info":{"aweme_id":78},"alliance_info":{"is_alliance_talent":"true"}},
                  {"aweme_user_info":{"aweme_id":79}}]}
                """);
            if(query(request).containsKey("filtering")) return response("""
                {"aweme_id_list":[{"aweme_id":77,"is_allow_mall":false},
                  {"aweme_id":78,"is_allow_mall":null},{"aweme_id":79,"is_allow_mall":"true"}]}
                """);
            return response("""
                {"aweme_id_list":[{"aweme_id":77,"auth_type":["AWEME_COOPERATOR"],"is_allow_mall":true},
                  {"aweme_id":78},{"aweme_id":79}]}
                """);
        });
        var accounts=adapter.loadAwemes("access","123");
        assertEquals(List.of("AWEME_COOPERATOR"),accounts.get(0).authTypes());
        assertEquals(Boolean.FALSE,accounts.get(0).isAllowMall());
        for(var a:accounts.subList(1,3)) { assertNull(a.isAllowMall()); assertTrue(a.authTypes().isEmpty()); }
    }

    @Test void optionalSourcesFailIndependentlyAndKeepPrimaryCatalog() {
        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec request=call.getArgument(0);
            if(query(request).containsKey("filtering")) throw new BizException(50001,"temporary failure");
            if(relations(request)) return response("{\"bind_aweme_user_infos\":[{\"aweme_user_info\":{\"aweme_id\":77},\"alliance_info\":{\"is_alliance_talent\":true}}]}");
            return response("{\"aweme_id_list\":[{\"aweme_id\":77,\"auth_type\":[\"OFFICIAL\"]}]}");
        });
        var a=adapter.loadAwemes("access","123").getFirst();
        assertEquals(List.of("OFFICIAL","ALLIANCE"),a.authTypes()); assertNull(a.isAllowMall());

        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec request=call.getArgument(0);
            if(relations(request)) throw new BizException(40000,"unavailable");
            return response("{\"aweme_id_list\":[{\"aweme_id\":77,\"auth_type\":[\"OFFICIAL\"],\"is_allow_mall\":true}]}");
        });
        a=adapter.loadAwemes("access","123").getFirst();
        assertEquals(List.of("OFFICIAL"),a.authTypes()); assertEquals(Boolean.TRUE,a.isAllowMall());
    }

    @Test void paginatesAllThreeSourcesUsingTheirOwnPaginationContract() throws Exception {
        Set<String> requests=new HashSet<>();
        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec request=call.getArgument(0); var query=query(request);
            boolean relation=relations(request),product=query.containsKey("filtering");
            int page=relation?json.readTree(query.get("page_params")).path("page").asInt():Integer.parseInt(query.get("page"));
            requests.add((relation?"relations":product?"product":"base")+page);
            int id=76+page;
            if(relation) return response("{\"bind_aweme_user_infos\":[{\"aweme_user_info\":{\"aweme_id\":"+id+"},\"alliance_info\":{\"is_alliance_talent\":true}}],\"pagination\":{\"total_page\":2}}");
            return response("{\"aweme_id_list\":[{\"aweme_id\":"+id+",\"auth_type\":[\"OFFICIAL\"],\"is_allow_mall\":"+(product?"true":"null")+"}],\"page_info\":{\"total_page\":2}}");
        });
        var accounts=adapter.loadAwemes("access","123");
        assertEquals(Set.of("base1","base2","product1","product2","relations1","relations2"),requests);
        assertEquals(List.of("77","78"),accounts.stream().map(a->a.awemeId()).toList());
        for(var a:accounts) { assertEquals(List.of("OFFICIAL","ALLIANCE"),a.authTypes()); assertEquals(Boolean.TRUE,a.isAllowMall()); }
    }

    @Test void failedLaterSupplementPageDoesNotPublishPartialLabels() {
        when(client.exchange(any(),eq(String.class))).thenAnswer(call->{
            RequestSpec request=call.getArgument(0); var query=query(request);
            if(relations(request)) return response("{\"bind_aweme_user_infos\":[]}");
            if(query.containsKey("filtering")) {
                if(query.get("page").equals("2")) throw new BizException(50001,"temporary failure");
                return response("{\"aweme_id_list\":[{\"aweme_id\":77,\"is_allow_mall\":true}],\"page_info\":{\"total_page\":2}}");
            }
            return response("{\"aweme_id_list\":[{\"aweme_id\":77,\"auth_type\":[\"OFFICIAL\"]}]}");
        });
        var a=adapter.loadAwemes("access","123").getFirst();
        assertNull(a.isAllowMall()); assertEquals(List.of("OFFICIAL"),a.authTypes());
    }

    private boolean relations(RequestSpec request) { return request.url().startsWith(properties.getQianchuan().getOauth().getAwemeRelationsUrl()); }
    private Map<String,String> query(RequestSpec request) {
        Map<String,String> values=new HashMap<>();
        for(String entry:URI.create(request.url()).getRawQuery().split("&")) {
            String[] pair=entry.split("=",2);
            values.put(pair[0],URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
        }
        return values;
    }
    private String response(String data) { return "{\"code\":0,\"data\":"+data+"}"; }
}
