package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.crypto.Encryptor;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.application.OauthCommandService;
import com.lynx.ad.modules.identity.application.TokenRefreshJob;
import com.lynx.ad.modules.identity.domain.port.OauthTokenPort;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
class TokenRefreshJobIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenRefreshJob job;

    @Autowired
    private OauthTokenPort tokenPort;

    @Autowired
    private OauthCommandService commandService;

    @Autowired
    private Encryptor encryptor;

    @Test
    void jobRefreshWritesNewCipherViaPort() throws Exception {
        Session a = onboard("13900008011", "刷新店");
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + a.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        String before = jdbc.queryForObject(
                "select access_token_enc from oauth_account where tenant_id = ?", String.class, a.tenantId);
        jdbc.update(
                "update oauth_account set token_expires_at = ? where tenant_id = ?",
                Timestamp.from(Instant.now().minusSeconds(10)),
                a.tenantId);
        OauthTokenPort spy = Mockito.spy(tokenPort);
        job.refreshDue();
        String after = jdbc.queryForObject(
                "select access_token_enc from oauth_account where tenant_id = ?", String.class, a.tenantId);
        assertNotEquals(before, after);
        assertNotEquals("callback-access", encryptor.decrypt(after));
        Mockito.verify(spy, Mockito.atMost(0)).exchange(Mockito.any());
    }

    @Test
    void jobFailMarksUnusableAndInbox() throws Exception {
        Session a = onboard("13900008012", "失败刷新店");
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + a.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        jdbc.update(
                "update oauth_account set token_expires_at = ?, refresh_token_enc = ? where tenant_id = ?",
                Timestamp.from(Instant.now().minusSeconds(10)),
                "not-valid-cipher",
                a.tenantId);
        job.refreshDue();
        String status = jdbc.queryForObject(
                "select status from oauth_account where tenant_id = ?", String.class, a.tenantId);
        assertEquals("UNUSABLE", status);
        Integer inbox = jdbc.queryForObject(
                "select count(*) from notification where tenant_id = ? and title = '授权已失效'",
                Integer.class,
                a.tenantId);
        assertTrue(inbox != null && inbox >= 1);
    }

    @Test
    void manualRefreshMissingAccountIs1201FailIs1211() throws Exception {
        Session a = onboard("13900008013", "手动刷新店");
        BizException missing = assertThrows(
                BizException.class, () -> commandService.refresh(new TenantId(a.tenantId), new AccountId("missing-acc")));
        assertEquals(1201, missing.code());

        mvc.perform(MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + a.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        String id = jdbc.queryForObject("select id from oauth_account where tenant_id = ?", String.class, a.tenantId);
        jdbc.update("update oauth_account set refresh_token_enc = 'bad' where id = ?", id);
        MvcResult resp = mvc.perform(MockMvcRequestBuilders.post("/api/v1/identity/accounts/" + id + "/refresh")
                        .header("Authorization", "Bearer " + a.token))
                .andReturn();
        JsonNode body = objectMapper.readTree(resp.getResponse().getContentAsString());
        assertEquals(1211, body.path("code").asInt());
        assertTrue(body.path("message").asText().contains("授权已失效"));
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
        return new Session(token, objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("tenantId").asText());
    }

    private record Session(String token, String tenantId) {}
}
