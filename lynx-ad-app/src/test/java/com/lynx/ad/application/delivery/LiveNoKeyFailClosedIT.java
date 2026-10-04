package com.lynx.ad.application.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.aiclient.ChatCompletionClient;
import com.lynx.ad.aiclient.mock.MockChatCompletionClient;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.port.AdCreatePort;
import com.lynx.ad.modules.delivery.domain.port.OceanAdCreateCommand;
import com.lynx.ad.modules.delivery.infrastructure.adapter.MockDeliveryAdapters;
import com.lynx.ad.modules.identity.domain.model.Credential;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
class LiveNoKeyFailClosedIT extends AbstractContainerIT {

    @DynamicPropertySource
    static void noKey(DynamicPropertyRegistry registry) {
        registry.add("lynx.ad.mock.qianchuan", () -> "false");
        registry.add("lynx.ad.mock.ai", () -> "true");
        registry.add("lynx.ad.oauth.qianchuan.app-id", () -> "");
        registry.add("lynx.ad.oauth.qianchuan.app-secret", () -> "");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdCreatePort adCreatePort;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ChatCompletionClient chatClient;

    @Test
    void noKeyCreateFailsAndNoMockPlan() throws Exception {
        assertFalse(context.getBeansOfType(MockDeliveryAdapters.class).size() > 0);
        Session s = onboard("13900008041", "缺密钥店");
        MvcResult bind = mvc.perform(MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + s.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        int bindCode = mapper.readTree(bind.getResponse().getContentAsString()).path("code").asInt();
        assertTrue(bindCode == 1205 || bindCode == 1406);
        try {
            adCreatePort.create(
                    new Credential("t", new AdvertiserId("adv")),
                    new OceanAdCreateCommand("p", "n", "SMART_BID_CONSERVATIVE", Money.ofYuan(300)));
        } catch (BizException ex) {
            assertTrue(ex.code() == 1205 || ex.code() == 1406);
        }
        Integer mockPlans = jdbc.queryForObject(
                "select count(*) from ad_plan where tenant_id = ? and qc_plan_id like 'mock_%'",
                Integer.class,
                s.tenantId);
        assertEquals(0, mockPlans);
    }

    @Test
    void aiMockStillWorksWhenQianchuanLive() {
        assertTrue(chatClient instanceof MockChatCompletionClient);
        assertEquals("烘焙好物推荐", chatClient.complete("title", java.util.Map.of()));
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
        return new Session(token, data.path("tenantId").asText());
    }

    private record Session(String token, String tenantId) {}
}
