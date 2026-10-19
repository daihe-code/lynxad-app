package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.application.billing.BillingItSupport;
import com.lynx.ad.starter.LynxAdApplication;
import com.lynx.ad.support.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(classes = LynxAdApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountLimitIT extends AbstractContainerIT {
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.lynx.ad.modules.billing.application.EntitlementQueryService entitlements;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void professionalTrialAllowsThreeAndRejectsFourthAccount() throws Exception {
        String token = BillingItSupport.onboard(mvc, mapper, "13904000012");
        JsonNode first = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + token));
        assertEquals(0, first.path("code").asInt());
        String account = first.path("data").path("accountId").asText();
        String tenant = jdbc.queryForObject("SELECT tenant_id FROM oauth_account WHERE id=?",String.class,account);
        assertEquals(3,entitlements.adAccountLimit(new com.lynx.ad.common.domain.Ids.TenantId(tenant)));
        // The demo binder uses a fixed advertiser ID; distinct isolated fixtures exercise the quota guard.
        for(int i=2;i<=3;i++) jdbc.update("INSERT INTO oauth_account(id,tenant_id,platform,advertiser_id,account_name,access_token_enc,status) SELECT ?,tenant_id,platform,?,account_name,access_token_enc,status FROM oauth_account WHERE id=?",java.util.UUID.randomUUID().toString(),"quota-fixture-"+i,account);
        JsonNode second = BillingItSupport.json(
                mapper,
                mvc,
                MockMvcRequestBuilders.post("/api/v1/identity/oauth/accounts/mock-bind")
                        .header("Authorization", "Bearer " + token));
        assertEquals(0, second.path("code").asInt());
        String tenantId = BillingItSupport.tenantId(mapper, mvc, token);
        Integer rows = jdbc.queryForObject(
                "select count(*) from oauth_account where tenant_id = ? and advertiser_id = 'adv_demo'",
                Integer.class,
                tenantId);
        assertEquals(1, rows);
        String status = jdbc.queryForObject(
                "select status from oauth_account where tenant_id = ? and advertiser_id = 'adv_demo'",
                String.class,
                tenantId);
        assertEquals("ACTIVE", status);
    }
}
