package com.lynx.ad.application.watcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.dataops.application.AuditPollJob;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public final class WatcherItSupport {

    private WatcherItSupport() {}

    public static String onboard(MockMvc mvc, ObjectMapper mapper, String phone, String company) throws Exception {
        post(mvc, "/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        String t = json(mapper, post(mvc, "/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"盯\",\"companyName\":\""
                                + company
                                + "\",\"password\":\"Passw0rd!\",\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andReturn();
        post(mvc, "/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        return json(mapper, post(mvc, "/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
    }

    public static String publishDelivering(MockMvc mvc, ObjectMapper mapper, AuditPollJob auditPollJob, String token)
            throws Exception {
        JsonNode bind = json(mapper, post(mvc, "/api/v1/identity/oauth/accounts/mock-bind", "{}", token)).path("data");
        String accountId = bind.path("accountId").asText();
        String awemeId = bind.path("awemes").get(0).path("awemeId").asText();
        post(mvc, "/api/v1/catalog/products/select", "{\"awemeId\":\"" + awemeId + "\",\"productIds\":[\"qp01\",\"qp02\"]}", token);
        String planId = json(
                        mapper,
                        post(
                                mvc,
                                "/api/v1/delivery/plans",
                                "{\"accountId\":\""
                                        + accountId
                                        + "\",\"awemeId\":\""
                                        + awemeId
                                        + "\",\"name\":\"盯盘计划\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\",\"qp02\"]}",
                                token))
                .path("data")
                .path("plan")
                .path("planId")
                .asText();
        String taskId = json(
                        mapper,
                        post(
                                mvc,
                                "/api/v1/creative/creatives/generate",
                                "{\"planId\":\"" + planId + "\",\"productIds\":[\"qp01\",\"qp02\"],\"mode\":\"STANDARD\"}",
                                token))
                .path("data")
                .path("taskId")
                .asText();
        String packageId = json(mapper, get(mvc, "/api/v1/creative/creatives/tasks/" + taskId, token))
                .path("data")
                .path("packageId")
                .asText();
        post(mvc, "/api/v1/creative/packages/" + packageId + "/confirm", "{}", token);
        post(mvc, "/api/v1/delivery/plans/" + planId + "/publish", "{\"idempotencyKey\":\"watch-1\"}", token);
        auditPollJob.pollPendingAudits();
        return planId;
    }

    public static String tenantId(MockMvc mvc, ObjectMapper mapper, String token) throws Exception {
        return json(mapper, get(mvc, "/api/v1/tenant/users/me", token)).path("data").path("tenantId").asText();
    }

    public static String post(MockMvc mvc, String url, String body, String auth) throws Exception {
        var req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            req.header("Authorization", "Bearer " + auth);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    public static String get(MockMvc mvc, String url, String auth) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + auth))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    public static JsonNode json(ObjectMapper mapper, String raw) throws Exception {
        return mapper.readTree(raw);
    }
}
