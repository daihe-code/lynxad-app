package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import com.lynx.ad.support.TenantAuthSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class MockBindClosedIT extends AbstractContainerIT {

    @DynamicPropertySource
    static void qianchuanOff(DynamicPropertyRegistry registry) {
        registry.add("lynx.ad.mock.qianchuan", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void mockBindClosedReturns1205AndInsertsNothing() throws Exception {
        TenantAuthSupport.Session session = TenantAuthSupport.onboard(mvc, mapper, "13900009103", "演示绑定关");
        Integer before = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ?", Integer.class, session.tenantId());
        MvcResult bind = mvc.perform(MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        int code = mapper.readTree(bind.getResponse().getContentAsString()).path("code").asInt();
        assertEquals(1205, code);
        Integer after = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ?", Integer.class, session.tenantId());
        assertEquals(before, after);
    }
}
