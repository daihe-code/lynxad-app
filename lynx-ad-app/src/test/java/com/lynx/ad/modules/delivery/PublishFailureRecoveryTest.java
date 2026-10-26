package com.lynx.ad.modules.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.domain.service.PublishFailureRecovery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublishFailureRecoveryTest {
    @Test void explicitCausesIdentifyTheResponsiblePageAndUnknownReasonsNeverDefaultToMaterials() throws Exception {
        try (var input = getClass().getResourceAsStream("/publish-failure-recovery.json")) {
            assertNotNull(input);
            var rows = new ObjectMapper().readTree(input);
            assertTrue(rows.size() > 20);
            for (var row : rows) assertEquals(row.path("recovery").asText(),
                    PublishFailureRecovery.classify(null, row.path("message").asText()), row.toString());
        }
    }
    @Test void credentialErrorCodeTakesPriorityAndNullMessageRemainsUnknown() {
        for (int code : new int[]{1201,1205,1211,40102,40105})
            assertEquals("ACCOUNTS", PublishFailureRecovery.classify(code,"预算不足"));
        assertEquals("UNKNOWN", PublishFailureRecovery.classify(null,null));
    }
}
