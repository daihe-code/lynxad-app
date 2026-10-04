package com.lynx.ad.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public final class TenantAuthSupport {

    private TenantAuthSupport() {}

    public static JsonNode json(ObjectMapper mapper, String raw) throws Exception {
        return mapper.readTree(raw);
    }

    public static String post(MockMvc mvc, String url, String body, String token) throws Exception {
        var req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    public static String put(MockMvc mvc, String url, String body, String token) throws Exception {
        var req = MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    public static String get(MockMvc mvc, String url, String token) throws Exception {
        var req = MockMvcRequestBuilders.get(url);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    public static String smsLogin(MockMvc mvc, ObjectMapper mapper, String phone) throws Exception {
        post(mvc, "/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        return json(mapper, post(mvc, "/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
                .path("data")
                .path("token")
                .asText();
    }

    public static Session onboard(MockMvc mvc, ObjectMapper mapper, String phone, String company) throws Exception {
        String token = smsLogin(mvc, mapper, phone);
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"测\",\"companyName\":\""
                                + company
                                + "\",\"password\":\"Passw0rd!\",\"agreeTerms\":true,\"agreePrivacy\":true}"))
                .andReturn();
        token = smsLogin(mvc, mapper, phone);
        MvcResult me = mvc.perform(MockMvcRequestBuilders.get("/api/v1/tenant/users/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        String tenantId = mapper.readTree(me.getResponse().getContentAsString()).path("data").path("tenantId").asText();
        return new Session(token, tenantId);
    }

    public record Session(String token, String tenantId) {}
}
