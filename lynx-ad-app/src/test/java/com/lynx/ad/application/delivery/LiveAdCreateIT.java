package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.dataops.domain.port.ReportPullPort;
import com.lynx.ad.modules.dataops.domain.service.AmountConversion;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.domain.model.PlanAction;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.domain.port.AdCreatePort;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
import com.lynx.ad.modules.delivery.domain.port.OceanAdCreateCommand;
import com.lynx.ad.modules.identity.domain.model.Credential;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.LiveFixtureServer;
import java.math.BigDecimal;
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

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveAdCreateIT extends AbstractContainerIT {

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
    private AdCreatePort adCreatePort;

    @Autowired
    private AdListPort adListPort;

    @Autowired
    private ReportPullPort reportPullPort;

    @Autowired
    private PlanCommandService planCommandService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createIdHasNoMockPrefixAndAppearsInList() {
        Credential cred = new Credential("tok", new AdvertiserId("adv_live_1"));
        var id = adCreatePort.create(
                cred, new OceanAdCreateCommand("p1", "真链计划", "SMART_BID_CONSERVATIVE", Money.ofYuan(300)));
        assertFalse(id.value().startsWith("mock_"));
        assertEquals("qc_live_1001", id.value());
        var rows = adListPort.list(cred, SmartBidType.CONSERVATIVE);
        assertTrue(rows.stream().anyMatch(r -> id.value().equals(r.qcPlanId())));
    }

    @Test
    void reportStatCostDividesOnceToTwoYuan() throws Exception {
        Session s = onboard("13900008021", "报表店");
        bindLive(s);
        String accountId = jdbc.queryForObject("select id from oauth_account where tenant_id = ?", String.class, s.tenantId);
        var pulled = reportPullPort.pull(new com.lynx.ad.modules.delivery.domain.model.AdPlan(
                new PlanId("plan-report-1"),
                new TenantId(s.tenantId),
                new com.lynx.ad.common.domain.Ids.AccountId(accountId),
                new com.lynx.ad.common.domain.Ids.AwemeId("aweme_live"),
                new com.lynx.ad.modules.delivery.domain.model.PlanName("报表计划"),
                com.lynx.ad.modules.delivery.domain.model.PlanStatus.DELIVERING,
                com.lynx.ad.modules.delivery.domain.model.PlanSource.LINGTOU,
                new com.lynx.ad.modules.delivery.domain.model.DeliverySetting(
                        SmartBidType.CONSERVATIVE, Money.ofYuan(300), java.util.Optional.empty(),
                        com.lynx.ad.modules.delivery.domain.model.Schedule.fromNow(),
                        com.lynx.ad.modules.delivery.domain.model.AudienceJson.empty()),
                java.util.List.of(),
                new QcPlanId("qc_live_1001"),
                "DELIVERING",
                null,
                1,
                null,
                java.util.List.of()));
        Money yuan = new AmountConversion().toYuan(pulled.costOcean());
        assertEquals(0, yuan.amount().compareTo(new BigDecimal("2.00")));
        assertEquals(200000, pulled.costOcean());
    }

    @Test
    void statusFailKeepsLocalState() throws Exception {
        Session s = onboard("13900008022", "启停店");
        bindLive(s);
        String accountId = jdbc.queryForObject("select id from oauth_account where tenant_id = ?", String.class, s.tenantId);
        MvcResult draft = mvc.perform(MockMvcRequestBuilders.post("/api/v1/delivery/plans")
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\""
                                + accountId
                                + "\",\"awemeId\":\"aweme_live\",\"name\":\"启停计划\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\"]}"))
                .andReturn();
        String planId = objectMapper.readTree(draft.getResponse().getContentAsString()).path("data").path("plan").path("planId").asText();
        jdbc.update("update ad_plan set qc_plan_id = 'qc_live_1001', status = 'DELIVERING' where id = ?", planId);
        String before = jdbc.queryForObject("select status from ad_plan where id = ?", String.class, planId);
        FIXTURE.statusFail = true;
        try {
            assertThrows(BizException.class, () -> planCommandService.act(new TenantId(s.tenantId), new PlanId(planId), PlanAction.DISABLE));
            String after = jdbc.queryForObject("select status from ad_plan where id = ?", String.class, planId);
            assertEquals(before, after);
        } finally {
            FIXTURE.statusFail = false;
        }
    }

    @Test
    void createFailIs1406AndNoSuccessPlan() throws Exception {
        Session s = onboard("13900008023", "失败创建店");
        bindLive(s);
        String accountId = jdbc.queryForObject("select id from oauth_account where tenant_id = ?", String.class, s.tenantId);
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/delivery/plans")
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\""
                                + accountId
                                + "\",\"awemeId\":\"aweme_live\",\"name\":\"失败计划\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\"]}"))
                .andReturn();
        FIXTURE.createFail = true;
        try {
            Credential cred = new Credential("tok", new AdvertiserId("adv_live_1"));
            BizException ex = assertThrows(
                    BizException.class,
                    () -> adCreatePort.create(
                            cred, new OceanAdCreateCommand("p2", "失败", "SMART_BID_CONSERVATIVE", Money.ofYuan(300))));
            assertEquals(1406, ex.code());
            Integer success = jdbc.queryForObject(
                    "select count(*) from ad_plan where tenant_id = ? and qc_plan_id is not null and qc_plan_id like 'mock_%'",
                    Integer.class,
                    s.tenantId);
            assertEquals(0, success);
        } finally {
            FIXTURE.createFail = false;
        }
    }

    private void bindLive(Session s) throws Exception {
        com.lynx.ad.modules.identity.domain.service.OauthStateSigner signer =
                new com.lynx.ad.modules.identity.domain.service.OauthStateSigner("dev-only-aes256-key-32bytes!!!!");
        String state = signer.sign(new TenantId(s.tenantId), new com.lynx.ad.common.domain.Ids.UserId(s.userId));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/callback")
                        .param("auth_code", "c-" + s.tenantId)
                        .param("state", state))
                .andReturn();
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
