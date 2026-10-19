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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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

    @BeforeEach
    void resetExchangeFixture() {
        restoreExchangeFixture();
    }

    @AfterEach
    void restoreExchangeFixtureAfter() {
        restoreExchangeFixture();
    }

    private static void restoreExchangeFixture() {
        FIXTURE.exchangeAdvertiserId = "adv_live_1";
        FIXTURE.exchangeAdvertiserIds = null;
        FIXTURE.exchangeAccessToken = "live-access";
        FIXTURE.exchangeAdvertiserName = "夹具账户";
        FIXTURE.awemeMode = "ok";
    }

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

        MvcResult reused = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "live-code-1")
                        .param("state", signer.sign(new TenantId(a.tenantId), new UserId(a.userId))))
                .andReturn();
        assertEquals(302, reused.getResponse().getStatus());
        String reusedLoc = reused.getResponse().getHeader("Location");
        assertTrue(reusedLoc != null && reusedLoc.contains("ok=0"));
        assertTrue(reusedLoc.contains(URLEncoder.encode("授权码已使用", StandardCharsets.UTF_8)));
        Integer advertisers = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ? and advertiser_id = 'adv_live_1'",
                Integer.class,
                a.tenantId);
        assertEquals(1, advertisers);
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

    @Test
    void secondAdvertiserThenReconfirmKeepsOriginalRow() throws Exception {
        Session session = onboard("13900008003", "多账户店");
        String limitMessage = URLEncoder.encode("当前套餐最多绑定", StandardCharsets.UTF_8);

        FIXTURE.exchangeAdvertiserId = "adv_live_1";
        FIXTURE.exchangeAccessToken = "live-access";
        FIXTURE.exchangeAdvertiserName = "夹具账户";
        String firstLoc = callback(session, "live-multi-1");
        assertTrue(firstLoc.contains("ok=1"));
        assertFalse(firstLoc.contains(limitMessage));
        String firstId = accountId(session.tenantId, "adv_live_1");
        String firstEnc = accessToken(session.tenantId, "adv_live_1");

        FIXTURE.exchangeAdvertiserId = "adv_live_2";
        FIXTURE.exchangeAccessToken = "live-access-2";
        FIXTURE.exchangeAdvertiserName = "夹具账户二";
        String secondLoc = callback(session, "live-multi-2");
        assertTrue(secondLoc.contains("ok=1"));
        assertFalse(secondLoc.contains(limitMessage));
        assertEquals(2, accountCount(session.tenantId));

        jdbc.update(
                "update oauth_account set status = 'UNBOUND', account_name = '旧名称', authorized_at = authorized_at - interval '1 minute' where id = ?",
                firstId);
        Timestamp backdated = jdbc.queryForObject(
                "select authorized_at from oauth_account where id = ?",
                Timestamp.class,
                firstId);

        FIXTURE.exchangeAdvertiserId = "adv_live_1";
        FIXTURE.exchangeAccessToken = "live-access-3";
        FIXTURE.exchangeAdvertiserName = "夹具账户-再次";
        String thirdLoc = callback(session, "live-multi-3");
        assertTrue(thirdLoc.contains("ok=1"));
        assertEquals(2, accountCount(session.tenantId));
        assertEquals(firstId, accountId(session.tenantId, "adv_live_1"));
        assertEquals("ACTIVE", accountColumn(session.tenantId, "adv_live_1", "status"));
        assertEquals("夹具账户-再次", accountColumn(session.tenantId, "adv_live_1", "account_name"));
        String thirdEnc = accessToken(session.tenantId, "adv_live_1");
        assertNotEquals(firstEnc, thirdEnc);
        assertFalse(thirdEnc.contains("live-access-3"));
        Timestamp renewed = jdbc.queryForObject(
                "select authorized_at from oauth_account where id = ?",
                Timestamp.class,
                firstId);
        assertTrue(renewed.after(backdated));

        String reusedLoc = callback(session, "live-multi-1");
        assertTrue(reusedLoc.contains("ok=0"));
        assertTrue(reusedLoc.contains(URLEncoder.encode("授权码已使用", StandardCharsets.UTF_8)));
        assertEquals(2, accountCount(session.tenantId));
    }

    @Test
    void awemeBusinessErrorPersistsNeitherAccountNorBinding() throws Exception {
        Session session = onboard("13900008021", "抖音失败店");
        FIXTURE.awemeMode = "error";
        String location = callback(session, "live-aweme-err");
        assertTrue(location.contains("ok=0"));
        assertTrue(location.contains(URLEncoder.encode("抖音号列表失败", StandardCharsets.UTF_8)));
        assertEquals(0, accountCount(session.tenantId));
        assertEquals(0, bindingCount(session.tenantId));
        assertEquals(0, placeholderAwemeCount(session.tenantId));
    }

    @Test
    void emptyAwemeListPersistsAccountAndQianchuanBinding() throws Exception {
        Session session = onboard("13900008022", "空抖音店");
        FIXTURE.awemeMode = "empty";
        String location = callback(session, "live-aweme-empty");
        assertTrue(location.contains("ok=1"));
        assertEquals(1, accountCount(session.tenantId));
        assertEquals(0, awemeCount(session.tenantId));
        assertEquals(0, placeholderAwemeCount(session.tenantId));
        assertEquals(1, bindingCount(session.tenantId));
        assertEquals("QIANCHUAN", bindingColumn(session.tenantId, "app_code"));
        assertEquals(accountId(session.tenantId, "adv_live_1"), bindingColumn(session.tenantId, "oauth_account_id"));
    }

    @Test
    void advertiserIdsWithoutSingleFieldKeepsOnlyFirst() throws Exception {
        Session session = onboard("13900008023", "双广告主店");
        FIXTURE.exchangeAdvertiserIds = "adv_ids_1,adv_ids_2";
        String location = callback(session, "live-ids-1");
        assertTrue(location.contains("ok=1"));
        assertEquals(1, accountCount(session.tenantId));
        assertEquals(1, bindingCount(session.tenantId));
        String account = accountId(session.tenantId, "adv_ids_1");
        assertEquals(account, bindingColumn(session.tenantId, "oauth_account_id"));
        Integer second = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ? and advertiser_id = 'adv_ids_2'",
                Integer.class,
                session.tenantId);
        assertEquals(0, second == null ? 0 : second);
    }

    @Test
    void secondSuccessKeepsSameAccountAndOneBinding() throws Exception {
        Session session = onboard("13900008024", "再次绑定店");
        String firstLocation = callback(session, "live-rebind-1");
        assertTrue(firstLocation.contains("ok=1"));
        String account = accountId(session.tenantId, "adv_live_1");
        String firstBinding = bindingColumn(session.tenantId, "id");

        FIXTURE.exchangeAdvertiserName = "夹具账户-再次";
        String secondLocation = callback(session, "live-rebind-2");
        assertTrue(secondLocation.contains("ok=1"));
        assertEquals(1, accountCount(session.tenantId));
        assertEquals(1, bindingCount(session.tenantId));
        assertEquals(account, accountId(session.tenantId, "adv_live_1"));
        assertEquals(account, bindingColumn(session.tenantId, "oauth_account_id"));
        assertEquals(firstBinding, bindingColumn(session.tenantId, "id"));
        assertEquals("夹具账户-再次", accountColumn(session.tenantId, "adv_live_1", "account_name"));
    }

    private String callback(Session session, String authCode) throws Exception {
        String state = signer.sign(new TenantId(session.tenantId), new UserId(session.userId));
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", authCode)
                        .param("state", state))
                .andReturn();
        assertEquals(302, result.getResponse().getStatus());
        String location = result.getResponse().getHeader("Location");
        assertTrue(location != null);
        return location;
    }

    private int accountCount(String tenantId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ?",
                Integer.class,
                tenantId);
        return count == null ? 0 : count;
    }

    private String accountId(String tenantId, String advertiserId) {
        return accountColumn(tenantId, advertiserId, "id");
    }

    private String accessToken(String tenantId, String advertiserId) {
        return accountColumn(tenantId, advertiserId, "access_token_enc");
    }

    private int bindingCount(String tenantId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from oauth_app_binding where tenant_id = ?",
                Integer.class,
                tenantId);
        return count == null ? 0 : count;
    }

    private int awemeCount(String tenantId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from oauth_aweme w join oauth_account a on a.id = w.oauth_account_id where a.tenant_id = ?",
                Integer.class,
                tenantId);
        return count == null ? 0 : count;
    }

    private int placeholderAwemeCount(String tenantId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from oauth_aweme w join oauth_account a on a.id = w.oauth_account_id where a.tenant_id = ? and w.name = ?",
                Integer.class,
                tenantId,
                "授权抖音号");
        return count == null ? 0 : count;
    }

    private String bindingColumn(String tenantId, String column) {
        return jdbc.queryForObject(
                "select " + column + " from oauth_app_binding where tenant_id = ?",
                String.class,
                tenantId);
    }

    private String accountColumn(String tenantId, String advertiserId, String column) {
        return jdbc.queryForObject(
                "select " + column + " from oauth_account where tenant_id = ? and advertiser_id = ?",
                String.class,
                tenantId,
                advertiserId);
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
