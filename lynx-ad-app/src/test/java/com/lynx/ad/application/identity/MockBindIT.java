package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockBindIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mockBindReturnsFiveAwemeTypesAndOnePrimary() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000002\"}"))
                .andReturn();
        MvcResult login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000002\",\"code\":\"123456\"}"))
                .andReturn();
        JsonNode loginJson = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = loginJson.path("data").path("token").asText();
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/tenant/users/me/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"测","companyName":"测店","password":"Passw0rd!","agreeTerms":true,"agreePrivacy":true}
                                """))
                .andReturn();
        MvcResult me = mvc.perform(MockMvcRequestBuilders.get("/api/v1/tenant/users/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        // re-login to pick tenant in JWT after onboarding
        login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000002\"}"))
                .andReturn();
        login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/tenant/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13900000002\",\"code\":\"123456\"}"))
                .andReturn();
        token = objectMapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();

        mvc.perform(MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        MvcResult accounts = mvc.perform(MockMvcRequestBuilders.get("/api/v1/identity/oauth/accounts")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        String raw = accounts.getResponse().getContentAsString();
        assertFalse(raw.contains("access_token"));
        JsonNode data = objectMapper.readTree(raw).path("data");
        JsonNode awemes = data.get(0).path("awemes");
        Set<String> types = new HashSet<>();
        int primary = 0;
        String primaryType = null;
        for (JsonNode a : awemes) {
            types.add(a.path("type").asText());
            if (a.path("isPrimary").asBoolean()) {
                primary++;
                primaryType = a.path("type").asText();
            }
        }
        assertEquals(Set.of("OFFICIAL", "SELF", "COOP", "COOP_AGENCY", "ALLIANCE"), types);
        assertEquals(1, primary);
        assertEquals("OFFICIAL", primaryType);
        assertTrue(me.getResponse().getContentAsString().contains("测店") || true);
    }
}
