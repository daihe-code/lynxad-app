package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.AssetReceiptLookupPort.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.QianchuanAssetReceiptLookup;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class AssetReceiptLookupTest {
    static final String HASH="0123456789abcdef0123456789abcdef";
    final ObjectMapper mapper=new ObjectMapper();final QianchuanRestClient client=mock(QianchuanRestClient.class);
    final LynxAdProperties properties=new LynxAdProperties();final Credential credential=new Credential("test-token",new AdvertiserId("123456"));
    QianchuanAssetReceiptLookup lookup;
    @BeforeEach void setup(){properties.getMock().setQianchuan(false);lookup=new QianchuanAssetReceiptLookup(client,new OceanJson(mapper),mapper,properties);}
    Expected expected(AssetKind kind){return new Expected(kind,HASH,42,800,800,true);}
    JsonNode row(String id,String material){return mapper.createObjectNode().put("id",id).put("audio_id",id).put("material_id",material).put("signature",HASH).put("size",42).put("width",800).put("height",800).put("is_ai_create",true);}
    void reply(JsonNode...rows){var data=mapper.createObjectNode();data.putArray("list").addAll(java.util.List.of(rows));data.putObject("page_info").put("total_page",1).put("total_number",rows.length);replyData(data);}
    void replyData(JsonNode data){when(client.exchange(any(),eq(String.class))).thenReturn(mapper.createObjectNode().put("code",0).set("data",data).toString());}
    @Test void allSupportedKindsReadMetadataWithExactAccountAndSignatureOnly() {
        for(var kind:java.util.List.of(AssetKind.IMAGE,AssetKind.VIDEO,AssetKind.AUDIO)) {
            clearInvocations(client);reply(row("original-file","9223372036854775806"));
            var result=lookup.find(credential,expected(kind));assertEquals("CONFIRMED",result.state());assertEquals("9223372036854775806",result.materialId());
            var request=ArgumentCaptor.forClass(RequestSpec.class);verify(client).exchange(request.capture(),eq(String.class));verifyNoMoreInteractions(client);
            var spec=request.getValue();assertEquals("GET",spec.method());assertNull(spec.body());assertEquals("test-token",spec.headers().get("Access-Token"));
            var url=URLDecoder.decode(spec.url(),StandardCharsets.UTF_8);assertTrue(url.contains("advertiser_id=123456"));assertTrue(url.contains("\"signatures\":[\""+HASH+"\"]"));assertTrue(url.contains("page_size=100"));
            assertTrue(url.contains(kind==AssetKind.AUDIO?"/file/audio/get/":kind==AssetKind.VIDEO?"/video/get/":"/image/get/"));
        }
    }
    @Test void fileNamesAndOtherAccountsCannotProveAReceipt() {
        var wrong=row("same-name","12").deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)wrong).put("signature","ffffffffffffffffffffffffffffffff").put("filename","test.jpg");reply(wrong);
        assertEquals("NOT_FOUND",lookup.find(credential,expected(AssetKind.IMAGE)).state());
        ((com.fasterxml.jackson.databind.node.ObjectNode)wrong).put("signature",HASH).put("advertiser_id","654321");reply(wrong);
        assertEquals("NOT_FOUND",lookup.find(credential,expected(AssetKind.IMAGE)).state());
    }
    @Test void multipleDifferentIdsRemainAmbiguousButIdenticalRowsDoNot() {
        reply(row("one","11"),row("two","12"));assertEquals("AMBIGUOUS",lookup.find(credential,expected(AssetKind.IMAGE)).state());
        reply(row("one","11"),row("one","11"));assertEquals("CONFIRMED",lookup.find(credential,expected(AssetKind.IMAGE)).state());
    }
    @Test void incompletePaginationOrIdentityNeverConfirmsOrFollowsMorePages() {
        var data=mapper.createObjectNode();data.putArray("list").add(row("one","11"));replyData(data);assertEquals("INCOMPLETE",lookup.find(credential,expected(AssetKind.IMAGE)).state());
        data.putObject("page_info").put("total_page",2).put("total_number",101);replyData(data);assertEquals("INCOMPLETE",lookup.find(credential,expected(AssetKind.IMAGE)).state());
        reply(row("one",""));assertEquals("INCOMPLETE",lookup.find(credential,expected(AssetKind.IMAGE)).state());
        var withoutAi=(com.fasterxml.jackson.databind.node.ObjectNode)row("one","11");withoutAi.remove("is_ai_create");reply(withoutAi);assertEquals("INCOMPLETE",lookup.find(credential,expected(AssetKind.IMAGE)).state());
        verify(client,times(4)).exchange(any(),eq(String.class));verifyNoMoreInteractions(client);
    }
    @Test void sizeDimensionAiAndPreviouslyKnownIdsMustAgree() {
        for(String field:java.util.List.of("size","width","height")) {var wrong=(com.fasterxml.jackson.databind.node.ObjectNode)row("one","11");wrong.put(field,99);reply(wrong);assertEquals("NOT_FOUND",lookup.find(credential,expected(AssetKind.IMAGE)).state());}
        var wrong=(com.fasterxml.jackson.databind.node.ObjectNode)row("one","11");wrong.put("is_ai_create",false);reply(wrong);assertEquals("NOT_FOUND",lookup.find(credential,expected(AssetKind.IMAGE)).state());
        reply(row("one","11"));assertEquals("NOT_FOUND",lookup.find(credential,new Expected(AssetKind.IMAGE,HASH,42,800,800,true,"other",null)).state());
        assertEquals("NOT_FOUND",lookup.find(credential,new Expected(AssetKind.IMAGE,HASH,42,800,800,true,null,"999")).state());
    }
    @Test void missingSignatureOrMockModeNeverContactsPlatform() {
        assertEquals("MISSING_EVIDENCE",lookup.find(credential,new Expected(AssetKind.IMAGE,"invalid",42,null,null,false)).state());
        properties.getMock().setQianchuan(true);assertEquals("UNAVAILABLE",lookup.find(credential,expected(AssetKind.IMAGE)).state());verifyNoInteractions(client);
    }
}
