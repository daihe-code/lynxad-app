package com.lynx.ad.application.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public final class BillingItSupport {

    private BillingItSupport() {}

    public static String onboard(MockMvc mvc, ObjectMapper mapper, String phone) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andReturn();
        String token = mapper.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"" + phone + "\",\"code\":\"123456\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .path("token")
                .asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"nickname\":\"订\",\"companyName\":\"订店\",\"password\":\"Passw0rd!\",\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andReturn();
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andReturn();
        return mapper.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"" + phone + "\",\"code\":\"123456\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .path("token")
                .asText();
    }

    public static JsonNode json(ObjectMapper mapper, MockMvc mvc, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        String raw = mvc.perform(req).andReturn().getResponse().getContentAsString();
        return mapper.readTree(raw);
    }

    public static void expireTrial(JdbcTemplate jdbc, String tenantId) {
        jdbc.update("update billing_trial set expires_at = now() - interval '1 day' where tenant_id = ?", tenantId);
    }

    public static String tenantId(ObjectMapper mapper, MockMvc mvc, String token) throws Exception {
        return mapper.readTree(mvc.perform(MockMvcRequestBuilders.get("/api/v1/tenant/users/me")
                                .header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .path("tenantId")
                .asText();
    }
}
