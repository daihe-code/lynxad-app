package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LibraryFailIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private MaterialLibraryPort materialLibraryPort;

    @Test
    void libraryRequestUsesCatalogWhileProviderIsUnavailable() throws Exception {
        when(materialLibraryPort.list(any())).thenThrow(new IllegalStateException("upstream"));
        String token = onboard("13900003004", "失败店");
        post("/api/v1/identity/oauth/accounts/mock-bind", "{}", token);
        JsonNode body = mapper.readTree(get("/api/v1/creative/library?from=2026-09-12&to=2026-09-18", token));
        assertEquals(0, body.path("code").asInt());
        assertTrue(body.path("data").path("items").isArray());
        assertTrue(!body.path("data").path("platformWarning").asText().isBlank());
    }

    private static void assertTrue(boolean cond) {
        org.junit.jupiter.api.Assertions.assertTrue(cond);
    }

    private String onboard(String phone, String company) throws Exception {
        post("/api/v1/tenant/auth/sms/send", "{\"phone\":\"" + phone + "\"}", null);
        String t = mapper.readTree(post("/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
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
        return mapper.readTree(post("/api/v1/tenant/auth/sms/login", "{\"phone\":\"" + phone + "\",\"code\":\"123456\"}", null))
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
}
