package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.identity.domain.service.OauthStateSigner;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.LiveFixtureServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.time.Instant;
import java.util.List;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveMaterialLibraryIT extends AbstractContainerIT {

    static final LiveFixtureServer FIXTURE;

    static {
        try {
            FIXTURE = new LiveFixtureServer();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void live(DynamicPropertyRegistry registry) {
        FIXTURE.register(registry);
    }

    @AfterAll
    static void stop() {
        FIXTURE.stop();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;
    @Autowired private PlatformMaterialCatalog catalog;
    @Autowired private OauthAccountRepository accounts;

    @Test
    void incompleteCatalogPublishRollsBackEarlierTypes() throws Exception {
        Session s = onboard("13900008034", "素材快照店");
        bind(s);
        var account = accounts.findByTenant(new TenantId(s.tenantId)).getFirst();
        var old = new MaterialLibraryPort.PlatformMaterial("old-object", "old-material", "旧素材.mp4",
                1024, null, null, 10, "VIDEO_VERTICAL", "EXTERNAL", Instant.now(), "", "", false, "NONE");
        var next = new MaterialLibraryPort.PlatformMaterial("new-object", "new-material", "新素材.mp4",
                1024, null, null, 10, "VIDEO_VERTICAL", "EXTERNAL", Instant.now(), "", "", false, "NONE");
        catalog.replace(s.tenantId, account.id().value(), account.advertiserId().value(), MaterialType.VIDEO, List.of(old));
        assertThrows(IllegalArgumentException.class, () -> catalog.replaceAll(s.tenantId, account.id().value(),
                account.advertiserId().value(), java.util.Map.of(MaterialType.VIDEO, List.of(next))));
        var rows = catalog.list(s.tenantId);
        assertEquals(1, rows.size());
        assertEquals("old-object", rows.getFirst().objectId());
    }

    @Test
    void liveLibraryMapsFields() throws Exception {
        Session s = onboard("13900008031", "素材店");
        bind(s);
        String raw = get("/api/v1/creative/library?type=VIDEO", s.token);
        JsonNode data = mapper.readTree(raw).path("data");
        assertTrue(data.path("qianchuanBound").asBoolean());
        assertTrue(data.path("items").isArray());
    }

    @Test
    void liveLibraryFailureDoesNotBlockCachedList() throws Exception {
        Session s = onboard("13900008032", "素材失败店");
        bind(s);
        var account = accounts.findByTenant(new TenantId(s.tenantId)).getFirst();
        catalog.replace(s.tenantId, account.id().value(), account.advertiserId().value(), MaterialType.VIDEO,
                List.of(new MaterialLibraryPort.PlatformMaterial("cached-object", "cached-material", "已缓存.mp4",
                        2048, null, null, 12, "VIDEO_VERTICAL", "EXTERNAL", Instant.now(),
                        "https://example.com/expired-signed-url", "https://example.com/expired-poster", false, "NONE")));
        FIXTURE.libraryFail = true;
        try {
            String raw = get("/api/v1/creative/library?type=VIDEO", s.token);
            JsonNode body = mapper.readTree(raw);
            assertEquals(0, body.path("code").asInt());
            assertTrue(body.path("data").path("items").isArray());
            JsonNode item = body.path("data").path("items").get(0);
            assertEquals("cached-object", item.path("objectId").asText());
            assertEquals("", item.path("posterUrl").asText());
            assertEquals("", item.path("playUrl").asText());
        } finally {
            FIXTURE.libraryFail = false;
        }
    }

    @Test
    void unboundIsNotError() throws Exception {
        Session s = onboard("13900008033", "未绑定店");
        String raw = get("/api/v1/creative/library?type=VIDEO", s.token);
        JsonNode data = mapper.readTree(raw).path("data");
        assertEquals(0, mapper.readTree(raw).path("code").asInt());
        assertFalse(data.path("qianchuanBound").asBoolean());
    }

    private void bind(Session s) throws Exception {
        OauthStateSigner signer = new OauthStateSigner("dev-only-aes256-key-32bytes!!!!");
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "lib-" + s.tenantId)
                        .param("state", signer.sign(new TenantId(s.tenantId), new UserId(s.userId))))
                .andReturn();
    }

    private String get(String url, String token) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Session onboard(String phone, String company) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andReturn();
        MvcResult login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"123456\"}"))
                .andReturn();
        String token = mapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"测\",\"companyName\":\""
                                + company
                                + "\",\"password\":\"Passw0rd!\",\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andReturn();
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andReturn();
        login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"123456\"}"))
                .andReturn();
        token = mapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        MvcResult me = mvc.perform(MockMvcRequestBuilders.get("/api/v1/tenant/users/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        JsonNode data = mapper.readTree(me.getResponse().getContentAsString()).path("data");
        return new Session(token, data.path("tenantId").asText(), data.path("userId").asText());
    }

    private record Session(String token, String tenantId, String userId) {}
}
