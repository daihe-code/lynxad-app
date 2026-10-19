package com.lynx.ad.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.modules.identity.domain.service.OauthStateSigner;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.LiveFixtureServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductLiveQueryIT extends AbstractContainerIT {

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
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private final OauthStateSigner signer = new OauthStateSigner("dev-only-aes256-key-32bytes!!!!");

    @BeforeEach
    void resetFixture() {
        restore();
    }

    @AfterEach
    void restoreFixture() {
        restore();
    }

    private static void restore() {
        FIXTURE.productEmpty = false;
        FIXTURE.productFail = false;
        FIXTURE.productAltFields = false;
        FIXTURE.exchangeAdvertiserId = "adv_live_1";
        FIXTURE.exchangeAdvertiserIds = null;
        FIXTURE.awemeMode = "ok";
    }

    @Test
    void emptyListReturnsNoProductsAndNoMockPlan() throws Exception {
        Session s = onboard("13900008211", "空商品店");
        bind(s, "prod-empty-" + s.tenantId);
        FIXTURE.productEmpty = true;
        JsonNode body = products(s, "aweme_live");
        assertEquals(0, body.path("code").asInt());
        assertTrue(body.path("data").isArray());
        assertEquals(0, body.path("data").size());
        Integer plans = jdbc.queryForObject(
                "select count(*) from ad_plan where tenant_id = ? and qc_plan_id like 'mock_%'",
                Integer.class,
                s.tenantId);
        assertEquals(0, plans);
    }

    @Test
    void missingAwemeIs1306AndDoesNotCallProductQuery() throws Exception {
        Session s = onboard("13900008212", "无号店");
        bind(s, "prod-miss-" + s.tenantId);
        int hits = FIXTURE.productHits.get();
        JsonNode body = products(s, "aweme_missing");
        assertEquals(1306, body.path("code").asInt());
        assertEquals("商品池查询失败", body.path("message").asText());
        assertEquals(hits, FIXTURE.productHits.get());
    }

    @Test
    void twoOwnersOfSameAwemeIs1306AndDoesNotCallProductQuery() throws Exception {
        Session s = onboard("13900008213", "双账户店");
        bind(s, "prod-own-a-" + s.tenantId);
        FIXTURE.exchangeAdvertiserId = "adv_live_2";
        bind(s, "prod-own-b-" + s.tenantId);
        int hits = FIXTURE.productHits.get();
        JsonNode body = products(s, "aweme_live");
        assertEquals(1306, body.path("code").asInt());
        assertEquals("商品池查询失败", body.path("message").asText());
        assertEquals(hits, FIXTURE.productHits.get());
    }

    @Test
    void selectUnknownProductIs1302AndWritesNothing() throws Exception {
        Session s = onboard("13900008214", "错号店");
        bind(s, "prod-sel-" + s.tenantId);
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/catalog/products/select")
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"awemeId\":\"aweme_live\",\"productIds\":[\"qp99\"]}"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1302, body.path("code").asInt());
        assertEquals("商品不存在", body.path("message").asText());
        Integer rows = jdbc.queryForObject(
                "select count(*) from product where tenant_id = ? and qc_product_id = 'qp99'",
                Integer.class,
                s.tenantId);
        assertEquals(0, rows);
    }

    private JsonNode products(Session s, String awemeId) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/v1/catalog/products")
                        .header("Authorization", "Bearer " + s.token)
                        .param("awemeId", awemeId))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void bind(Session s, String authCode) throws Exception {
        String state = signer.sign(new TenantId(s.tenantId), new UserId(s.userId));
        MvcResult callback = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", authCode)
                        .param("state", state))
                .andReturn();
        assertEquals(302, callback.getResponse().getStatus());
        String location = callback.getResponse().getHeader("Location");
        assertTrue(location != null && location.contains("ok=1"), location);
    }

    private Session onboard(String phone, String company) {
        try {
            mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + phone + "\"}"))
                    .andReturn();
            MvcResult login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + phone + "\",\"code\":\"123456\"}"))
                    .andReturn();
            String token = objectMapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
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
            token = objectMapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
            MvcResult me = mvc.perform(MockMvcRequestBuilders.get("/api/v1/tenant/users/me")
                            .header("Authorization", "Bearer " + token))
                    .andReturn();
            JsonNode data = objectMapper.readTree(me.getResponse().getContentAsString()).path("data");
            return new Session(token, data.path("tenantId").asText(), data.path("userId").asText());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Session(String token, String tenantId, String userId) {}
}
