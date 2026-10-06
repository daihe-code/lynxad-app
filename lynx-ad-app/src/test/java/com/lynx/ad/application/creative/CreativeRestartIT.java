package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CreativeRestartIT extends AbstractContainerIT {

    static String token;
    static String taskId;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void generatePersistsSucceededTask() throws Exception {
        token = onboard("13900002001", "重启店");
        JsonNode bind = json(post("/api/v1/identity/oauth/accounts/mock-bind", "{}", token)).path("data");
        String accountId = bind.path("accountId").asText();
        String awemeId = bind.path("awemes").get(0).path("awemeId").asText();
        post("/api/v1/catalog/products/select", "{\"awemeId\":\"" + awemeId + "\",\"productIds\":[\"qp01\",\"qp02\"]}", token);
        String planId = json(post(
                        "/api/v1/delivery/plans",
                        "{\"accountId\":\""
                                + accountId
                                + "\",\"awemeId\":\""
                                + awemeId
                                + "\",\"name\":\"重启计划\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\",\"qp02\"]}",
                        token))
                .path("data")
                .path("plan")
                .path("planId")
                .asText();
        taskId = json(post(
                        "/api/v1/creative/creatives/generate",
                        "{\"planId\":\"" + planId + "\",\"productIds\":[\"qp01\",\"qp02\"],\"mode\":\"STANDARD\"}",
                        token))
                .path("data")
                .path("taskId")
                .asText();
        JsonNode task = com.lynx.ad.support.CreativeTaskSupport.awaitSucceeded(mvc, mapper, taskId, token);
        assertEquals("SUCCEEDED", task.path("status").asText());
        assertTrue(task.path("packageId").asText().length() > 0);
    }

    @Test
    @Order(2)
    void afterRestartSameTaskRemains() throws Exception {
        JsonNode task = json(get("/api/v1/creative/creatives/tasks/" + taskId, token)).path("data");
        assertEquals(taskId, task.path("taskId").asText());
        assertEquals("SUCCEEDED", task.path("status").asText());
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
