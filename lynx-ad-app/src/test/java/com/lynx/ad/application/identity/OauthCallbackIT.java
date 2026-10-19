package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.modules.identity.domain.service.OauthStateSigner;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OauthCallbackIT extends AbstractContainerIT {

    private static final String WEB_CB = "http://localhost:5174/oauth/callback";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private final OauthStateSigner signer = new OauthStateSigner("dev-only-aes256-key-32bytes!!!!");

    @Test
    void validHmacAndMockCodePersistsEncryptedAccount() throws Exception {
        Session a = onboard("13900001001", "回调店A");
        String state = signer.sign(new TenantId(a.tenantId), new UserId(a.userId));
        MvcResult cb = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "mock")
                        .param("state", state))
                .andReturn();
        String location = cb.getResponse().getHeader("Location");
        String body = cb.getResponse().getContentAsString();
        assertEquals(302, cb.getResponse().getStatus());
        assertTrue(location != null && location.startsWith(WEB_CB));
        assertTrue(location.contains("ok=1"));
        assertFalse(location.contains("callback-access"));
        assertFalse(location.contains("callback-refresh"));
        assertFalse(body.contains("callback-access"));
        assertFalse(body.contains("access_token"));

        Integer rows = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ? and advertiser_id = 'adv_oauth_mock'",
                Integer.class,
                a.tenantId);
        assertEquals(1, rows);
        String enc = jdbc.queryForObject(
                "select access_token_enc from oauth_account where tenant_id = ? and advertiser_id = 'adv_oauth_mock'",
                String.class,
                a.tenantId);
        assertNotEquals("callback-access", enc);
        assertFalse(enc.contains("callback-access"));

        MvcResult accounts = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/accounts")
                        .header("Authorization", "Bearer " + a.token))
                .andReturn();
        String raw = accounts.getResponse().getContentAsString();
        assertFalse(raw.contains("access_token"));
        JsonNode data = objectMapper.readTree(raw).path("data");
        JsonNode oauth = null;
        for (JsonNode n : data) {
            if ("adv_oauth_mock".equals(n.path("advertiserId").asText())) {
                oauth = n;
            }
        }
        assertTrue(oauth != null);
        Set<String> types = new HashSet<>();
        int primary = 0;
        for (JsonNode aw : oauth.path("awemes")) {
            types.add(aw.path("type").asText());
            if (aw.path("isPrimary").asBoolean()) {
                primary++;
            }
        }
        assertEquals(Set.of("OFFICIAL", "SELF", "COOP", "COOP_AGENCY", "ALLIANCE"), types);
        assertEquals(1, primary);

        Session b = onboard("13900001002", "回调店B");
        MvcResult other = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/accounts")
                        .header("Authorization", "Bearer " + b.token))
                .andReturn();
        String otherRaw = other.getResponse().getContentAsString();
        assertFalse(otherRaw.contains("adv_oauth_mock"));
    }

    @Test
    void invalidOrExpiredStateDoesNotPersist() throws Exception {
        Session a = onboard("13900001003", "失败店");
        String expired = signer.sign(new TenantId(a.tenantId), new UserId(a.userId), Instant.now().minusSeconds(30));
        MvcResult expiredCb = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "mock")
                        .param("state", expired))
                .andReturn();
        assertEquals(302, expiredCb.getResponse().getStatus());
        String expiredLoc = expiredCb.getResponse().getHeader("Location");
        assertTrue(expiredLoc != null && expiredLoc.contains("ok=0") && expiredLoc.contains("status=fail"));

        String valid = signer.sign(new TenantId(a.tenantId), new UserId(a.userId));
        String tampered = valid.substring(0, valid.length() - 2) + "xx";
        MvcResult bad = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "mock")
                        .param("state", tampered))
                .andReturn();
        assertEquals(302, bad.getResponse().getStatus());
        String badLoc = bad.getResponse().getHeader("Location");
        assertTrue(badLoc != null && badLoc.contains("ok=0") && badLoc.contains("status=fail"));

        Integer rows = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ? and advertiser_id = 'adv_oauth_mock'",
                Integer.class,
                a.tenantId);
        assertEquals(0, rows);
    }

    @Test
    void authorizeUrlCarriesStateAndHidesAccessToken() throws Exception {
        Session a = onboard("13900001004", "地址店");
        MvcResult url = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/url")
                        .header("Authorization", "Bearer " + a.token))
                .andReturn();
        String raw = url.getResponse().getContentAsString();
        assertFalse(raw.contains("access_token"));
        assertTrue(objectMapper.readTree(raw).path("data").path("url").asText().contains("state="));
    }

    @Test
    void missingAuthCodeDoesNotPersist() throws Exception {
        Session a = onboard("13900001005", "缺码店");
        String state = signer.sign(new TenantId(a.tenantId), new UserId(a.userId));
        MvcResult cb = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("state", state))
                .andReturn();
        assertEquals(302, cb.getResponse().getStatus());
        String loc = cb.getResponse().getHeader("Location");
        assertTrue(loc != null && loc.contains("ok=0"));
        assertTrue(loc.contains(URLEncoder.encode("缺少授权码", StandardCharsets.UTF_8)));
        Integer rows = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ?",
                Integer.class,
                a.tenantId);
        assertEquals(0, rows);
    }

    @Test
    void relayShowsParamsAndDoesNotPersist() throws Exception {
        Integer accountsBefore = jdbc.queryForObject("select count(*) from oauth_account", Integer.class);
        Integer auditsBefore = jdbc.queryForObject("select count(*) from oauth_auth_code_audit", Integer.class);
        MvcResult page = mvc.perform(MockMvcRequestBuilders.get(
                        "/api/v1/identity/oauth/relay?auth_code=relay-code&state=state-1"))
                .andReturn();
        assertEquals(200, page.getResponse().getStatus());
        assertTrue(page.getResponse().getContentType().contains("text/html"));
        assertEquals("no-store", page.getResponse().getHeader("Cache-Control"));
        String body = page.getResponse().getContentAsString();
        assertTrue(body.contains("auth_code = relay-code"));
        assertTrue(body.contains("state = state-1"));
        assertTrue(body.contains(
                "http://localhost:8080/lynx-ad/api/v1/identity/oauth/callback?auth_code=relay-code&amp;state=state-1"));
        assertEquals(accountsBefore, jdbc.queryForObject("select count(*) from oauth_account", Integer.class));
        assertEquals(auditsBefore, jdbc.queryForObject("select count(*) from oauth_auth_code_audit", Integer.class));
    }

    @Test
    void relayWithoutQueryShowsEmptyMessage() throws Exception {
        MvcResult page = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/relay"))
                .andReturn();
        assertEquals(200, page.getResponse().getStatus());
        String body = page.getResponse().getContentAsString();
        assertTrue(body.contains("未收到回调参数"));
        assertFalse(body.contains("完整地址"));
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
        JsonNode meJson = objectMapper.readTree(me.getResponse().getContentAsString()).path("data");
        return new Session(token, meJson.path("tenantId").asText(), meJson.path("userId").asText());
    }

    private record Session(String token, String tenantId, String userId) {}
}
