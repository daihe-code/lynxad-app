package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.modules.identity.api.IdentityOauthController;
import com.lynx.ad.modules.identity.domain.model.OauthAuthCodeAudit;
import com.lynx.ad.modules.identity.domain.service.OauthStateSigner;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.LiveFixtureServer;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OauthLiveExchangeIT extends AbstractContainerIT {

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

    @Test
    void liveExchangePersistsCipherAndOperatorFromFourPartState() throws Exception {
        Session a = onboard("13900008001", "真链店");
        String state = signer.sign(new TenantId(a.tenantId), new UserId(a.userId));
        MvcResult cb = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "live-code-1")
                        .param("state", state))
                .andReturn();
        assertEquals(302, cb.getResponse().getStatus());
        String loc = cb.getResponse().getHeader("Location");
        assertTrue(loc != null && loc.contains("ok=1"));
        assertFalse(cb.getResponse().getContentAsString().contains("live-access"));

        String enc = jdbc.queryForObject(
                "select access_token_enc from oauth_account where tenant_id = ? and advertiser_id = '991001'",
                String.class,
                a.tenantId);
        assertNotEquals("live-access", enc);
        assertFalse(enc.contains("live-access"));
        String operator = jdbc.queryForObject(
                "select authorized_by_user_id from oauth_account where tenant_id = ? and advertiser_id = '991001'",
                String.class,
                a.tenantId);
        assertEquals(a.userId, operator);

        String hash = jdbc.queryForObject(
                "select auth_code_hash from oauth_auth_code_audit where tenant_id = ?",
                String.class,
                a.tenantId);
        assertEquals(64, hash.length());
        assertEquals(OauthAuthCodeAudit.sha256Hex("live-code-1"), hash);
        assertFalse(hash.contains("live-code-1"));
        String auditUser = jdbc.queryForObject(
                "select user_id from oauth_auth_code_audit where tenant_id = ?",
                String.class,
                a.tenantId);
        assertEquals(a.userId, auditUser);

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "live-code-1")
                        .param("state", signer.sign(new TenantId(a.tenantId), new UserId(a.userId))))
                .andReturn();
        Integer used = jdbc.queryForObject(
                "select count(*) from oauth_auth_code_audit where tenant_id = ? and status = 'USED'",
                Integer.class,
                a.tenantId);
        assertEquals(1, used);
    }

    @Test
    void threePartStateIs1206AndDoesNotPersist() throws Exception {
        Session a = onboard("13900008002", "旧state店");
        String legacy = signer.sign(new TenantId(a.tenantId));
        MvcResult cb = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "legacy-code")
                        .param("state", legacy))
                .andReturn();
        assertEquals(302, cb.getResponse().getStatus());
        assertTrue(cb.getResponse().getHeader("Location").contains("ok=0"));
        Integer rows = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ?",
                Integer.class,
                a.tenantId);
        assertEquals(0, rows);
    }

    @Test
    void onlyOneCallbackMapping() {
        int mappings = 0;
        for (Method m : IdentityOauthController.class.getDeclaredMethods()) {
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get == null) {
                continue;
            }
            for (String p : get.value()) {
                if (p.contains("callback")) {
                    mappings++;
                    assertEquals("/api/v1/identity/oauth/callback", p);
                }
            }
        }
        assertEquals(1, mappings);
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
    }

    private record Session(String token, String tenantId, String userId) {}
}
