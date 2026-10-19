package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRecordRemoveIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void unboundAccountIsRemovedAndPlanRemains() throws Exception {
        String token = onboard("13900008321", "删除店");
        String accountId = bind(token);
        String tenantId = jdbc.queryForObject("select tenant_id from oauth_account where id = ?", String.class, accountId);
        String planId = UUID.randomUUID().toString();
        jdbc.update(
                "insert into ad_plan (id, tenant_id, oauth_account_id, aweme_id, name, status, qc_plan_id) values (?, ?, ?, 'aweme_demo', '历史计划', 'FINISHED', 'mock_seed1')",
                planId,
                tenantId,
                accountId);
        JsonNode unbound = json(mvc.perform(MockMvcRequestBuilders.delete("/api/v1/identity/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertEquals(0, unbound.path("code").asInt());
        JsonNode removed = json(post("/api/v1/identity/accounts/" + accountId + "/remove", "{}", token));
        assertEquals(0, removed.path("code").asInt());
        Integer accounts = jdbc.queryForObject("select count(*) from oauth_account where id = ?", Integer.class, accountId);
        assertEquals(0, accounts);
        Integer plans = jdbc.queryForObject("select count(*) from ad_plan where id = ?", Integer.class, planId);
        assertEquals(1, plans);
        String linked = jdbc.queryForObject("select oauth_account_id from ad_plan where id = ?", String.class, planId);
        assertTrue(linked == null || linked.isBlank());
        JsonNode list = json(get("/api/v1/identity/oauth/accounts", token)).path("data");
        for (JsonNode row : list) {
            assertFalse(accountId.equals(row.path("accountId").asText()));
        }
    }

    @Test
    void activeAccountIsRejected() throws Exception {
        String token = onboard("13900008322", "有效店");
        String accountId = bind(token);
        JsonNode body = json(post("/api/v1/identity/accounts/" + accountId + "/remove", "{}", token));
        assertEquals(1208, body.path("code").asInt());
        assertEquals("授权仍有效，请先取消授权", body.path("message").asText());
        Integer accounts = jdbc.queryForObject("select count(*) from oauth_account where id = ?", Integer.class, accountId);
        assertEquals(1, accounts);
        JsonNode list = json(get("/api/v1/identity/oauth/accounts", token)).path("data");
        boolean found = false;
        for (JsonNode row : list) {
            if (accountId.equals(row.path("accountId").asText())) {
                found = true;
                assertFalse(row.path("refreshExpiresAt").isMissingNode());
                assertFalse(row.path("refreshExpiresAt").isNull());
            }
        }
        assertTrue(found);
    }

    @Test
    void expiredRefreshCanBeRemoved() throws Exception {
        String token = onboard("13900008323", "过期店");
        String accountId = bind(token);
        jdbc.update("update oauth_account set refresh_expires_at = '2020-01-01 00:00:00' where id = ?", accountId);
        JsonNode before = json(get("/api/v1/identity/oauth/accounts", token)).path("data");
        boolean stamped = false;
        for (JsonNode row : before) {
            if (accountId.equals(row.path("accountId").asText())) {
                stamped = !row.path("refreshExpiresAt").isNull();
            }
        }
        assertTrue(stamped);
        JsonNode removed = json(post("/api/v1/identity/accounts/" + accountId + "/remove", "{}", token));
        assertEquals(0, removed.path("code").asInt());
        Integer accounts = jdbc.queryForObject("select count(*) from oauth_account where id = ?", Integer.class, accountId);
        assertEquals(0, accounts);
    }

    private String bind(String token) throws Exception {
        return json(post("/api/v1/identity/oauth/accounts/mock-bind", "{}", token))
                .path("data")
                .path("accountId")
                .asText();
    }

    private String onboard(String phone, String company) throws Exception {
        post("/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        String t = json(post("/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"测\",\"companyName\":\""
                                + company
                                + "\",\"password\":\"Passw0rd!\",\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andReturn();
        post("/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        return json(post("/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
    }

    private String post(String url, String body, String auth) throws Exception {
        var req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            req.header("Authorization", "Bearer " + auth);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    private String get(String url, String auth) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + auth))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }
}
