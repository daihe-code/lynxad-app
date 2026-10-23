package com.lynx.ad.support;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** A second loopback port must not relax the dedicated-schema or host boundary. */
class IsolatedStoreTestUrlTest {
    @Test void dedicatedLoopbackPortKeepsSchemaAndHostGuards() throws Exception {
        var suites=Map.of(
                "com.lynx.ad.modules.admin.PlatformAdminStoreIT","lynxad_admin_verify_fixture",
                "com.lynx.ad.modules.agentops.application.OptimizationStoreIT","lynxad_optimization_verify_fixture",
                "com.lynx.ad.modules.catalog.application.ProductPriceStoreIT","lynxad_prices_verify_fixture",
                "com.lynx.ad.modules.creative.MaterialCreativeContextStoreIT","lynxad_context_verify_fixture",
                "com.lynx.ad.modules.delivery.infrastructure.persistence.PlanDeletionRepositoryIT","lynxad_plan_delete_verify_fixture",
                "com.lynx.ad.modules.delivery.PlanInsightsStoreIT","lynxad_plan_insights_verify_fixture",
                "com.lynx.ad.modules.delivery.PlanProductChangeStoreIT","lynxad_products_verify_fixture",
                "com.lynx.ad.modules.delivery.PlanRemoteStoreIT","lynxad_transfer_verify_fixture",
                "com.lynx.ad.modules.delivery.PlanTransferStoreIT","lynxad_transfer_verify_fixture");
        for(var entry:suites.entrySet()) {
            var type=Class.forName(entry.getKey(),false,getClass().getClassLoader());
            var rule=Pattern.compile(type.getAnnotation(EnabledIfEnvironmentVariable.class).matches());
            String suffix="/"+entry.getValue()+"?useSSL=false";
            assertTrue(rule.matcher("jdbc:mysql://127.0.0.1:3306"+suffix).matches(),entry.getKey());
            assertTrue(rule.matcher("jdbc:mysql://127.0.0.1:55406"+suffix).matches(),entry.getKey());
            for(String unsafe:new String[]{"jdbc:mysql://10.0.0.1:55406"+suffix,
                    "jdbc:mysql://127.0.0.1:55406/lynx-ad-app?useSSL=false",
                    "jdbc:mysql://127.0.0.1:3307"+suffix,
                    "jdbc:mysql://127.0.0.1:55406/mysql?useSSL=false"})
                assertFalse(rule.matcher(unsafe).matches(),unsafe);
        }
    }
}
