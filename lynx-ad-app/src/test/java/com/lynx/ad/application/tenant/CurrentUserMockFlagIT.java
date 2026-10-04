package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.TenantAuthSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

class CurrentUserMockFlagIT {

    @Nested
    @SpringBootTest(classes = LynxAdApplication.class)
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class FlagOn extends AbstractContainerIT {

        @Autowired
        private MockMvc mvc;

        @Autowired
        private ObjectMapper mapper;

        @Test
        void meFlagOnAndNoTokens() throws Exception {
            TenantAuthSupport.Session session = TenantAuthSupport.onboard(mvc, mapper, "13900009101", "演示投放开");
            String raw = TenantAuthSupport.get(mvc, "/api/v1/tenant/users/me", session.token());
            assertFalse(raw.contains("accessToken"));
            assertFalse(raw.contains("refreshToken"));
            assertFalse(raw.contains("access_token"));
            assertFalse(raw.contains("refresh_token"));
            JsonNode data = mapper.readTree(raw).path("data");
            assertTrue(data.has("mockQianchuan"));
            assertTrue(data.path("mockQianchuan").asBoolean());
        }
    }

    @Nested
    @SpringBootTest(classes = LynxAdApplication.class)
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class FlagOff extends AbstractContainerIT {

        @DynamicPropertySource
        static void qianchuanOff(DynamicPropertyRegistry registry) {
            registry.add("lynx.ad.mock.qianchuan", () -> "false");
        }

        @Autowired
        private MockMvc mvc;

        @Autowired
        private ObjectMapper mapper;

        @Test
        void meFlagOff() throws Exception {
            TenantAuthSupport.Session session = TenantAuthSupport.onboard(mvc, mapper, "13900009102", "演示投放关");
            String raw = TenantAuthSupport.get(mvc, "/api/v1/tenant/users/me", session.token());
            JsonNode data = mapper.readTree(raw).path("data");
            assertTrue(data.has("mockQianchuan"));
            assertFalse(data.path("mockQianchuan").asBoolean());
        }
    }
}
