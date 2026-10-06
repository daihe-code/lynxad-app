package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LibraryFlowIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void filterDefaultWeekAndRejectInvertedRange() throws Exception {
        String token = onboard("13900003001", "滤镜店");
        JsonNode bad = json(get("/api/v1/creative/library?from=2026-09-18&to=2026-09-10", token));
        assertEquals(1509, bad.path("code").asInt());
        JsonNode ok = json(get("/api/v1/creative/library?from=2026-09-12&to=2026-09-18&pageSize=8", token));
        assertEquals(0, ok.path("code").asInt());
        assertEquals(8, ok.path("data").path("pageInfo").path("pageSize").asInt());
    }

    @Test
    void unboundHasNoPlatformSeeds() throws Exception {
        String token = onboard("13900003002", "未绑店");
        JsonNode body = json(get("/api/v1/creative/library?from=2026-09-12&to=2026-09-18", token));
        assertFalse(body.path("data").path("qianchuanBound").asBoolean());
        for (JsonNode item : body.path("data").path("items")) {
            assertEquals("LYNX", item.path("source").asText());
        }
    }

    @Test
    void boundMergeUsedAndCompat() throws Exception {
        String token = onboard("13900003003", "合并店");
        JsonNode bind = json(post("/api/v1/identity/oauth/accounts/mock-bind", "{}", token)).path("data");
        String accountId = bind.path("accountId").asText();
        String awemeId = bind.path("awemes").get(0).path("awemeId").asText();
        post("/api/v1/catalog/products/select", "{\"awemeId\":\"" + awemeId + "\",\"productIds\":[\"qp01\"]}", token);
        String planId = json(post(
                        "/api/v1/delivery/plans",
                        "{\"accountId\":\""
                                + accountId
                                + "\",\"awemeId\":\""
                                + awemeId
                                + "\",\"name\":\"素材绑定计划\",\"smartBidType\":\"SMART_BID_CONSERVATIVE\",\"dailyBudget\":300,\"productIds\":[\"qp01\"]}",
                        token))
                .path("data")
                .path("plan")
                .path("planId")
                .asText();
        String taskId = json(post(
                        "/api/v1/creative/creatives/generate",
                        "{\"planId\":\"" + planId + "\",\"productIds\":[\"qp01\"],\"mode\":\"STANDARD\"}",
                        token))
                .path("data")
                .path("taskId")
                .asText();
        JsonNode task = com.lynx.ad.support.CreativeTaskSupport.awaitSucceeded(mvc, mapper, taskId, token);
        assertEquals("SUCCEEDED", task.path("status").asText());
        assertTrue(task.path("items").isArray());
        String packageId = task.path("packageId").asText();
        post("/api/v1/creative/packages/" + packageId + "/confirm", "{}", token);
        post("/api/v1/delivery/plans/" + planId + "/publish", "{}", token);

        String to = LocalDate.now(ZoneOffset.UTC).toString();
        JsonNode lib = json(get("/api/v1/creative/library?from=2026-09-12&to=" + to + "&type=VIDEO", token));
        assertTrue(lib.path("data").path("qianchuanBound").asBoolean());
        boolean lynx = false;
        boolean ext = false;
        boolean used = false;
        for (JsonNode item : lib.path("data").path("items")) {
            if ("LYNX".equals(item.path("source").asText())) {
                lynx = true;
                if (item.path("used").asBoolean()) {
                    used = true;
                }
            }
            if ("EXTERNAL".equals(item.path("source").asText())) {
                ext = true;
            }
        }
        assertTrue(lynx);
        assertTrue(ext);
        assertTrue(used);

        JsonNode videos = json(get("/api/v1/creative/library/videos", token));
        assertTrue(videos.path("data").isArray());
        assertTrue(videos.path("data").get(0).has("videoId"));
        assertTrue(videos.path("data").get(0).has("productId"));
        assertTrue(videos.path("data").get(0).has("coverImageId"));
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
