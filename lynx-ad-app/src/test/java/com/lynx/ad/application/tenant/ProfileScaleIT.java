package com.lynx.ad.application.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.CompanyScale;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.TenantAuthSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileScaleIT extends AbstractContainerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void validScaleRoundTripsInvalidIs1604() throws Exception {
        var session = TenantAuthSupport.onboard(mvc, mapper, "13900002007", "规模店");
        JsonNode put = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.put(
                        mvc,
                        "/api/v1/tenant/users/me/profile",
                        "{\"nickname\":\"测\",\"email\":\"a@b.c\",\"companyName\":\"规模店\",\"industry\":\"零售\",\"companyScale\":\""
                                + CompanyScale.SIZE_1_10
                                + "\"}",
                        session.token()));
        assertEquals(0, put.path("code").asInt());
        JsonNode me = TenantAuthSupport.json(mapper, TenantAuthSupport.get(mvc, "/api/v1/tenant/users/me", session.token()));
        assertEquals(CompanyScale.SIZE_1_10, me.path("data").path("companyScale").asText());
        JsonNode bad = TenantAuthSupport.json(
                mapper,
                TenantAuthSupport.put(
                        mvc,
                        "/api/v1/tenant/users/me/profile",
                        "{\"companyScale\":\"一万亿人\"}",
                        session.token()));
        assertEquals(1604, bad.path("code").asInt());
        assertTrue(me.path("data").path("displayPlanName").asText().contains("专业"));
    }
}
