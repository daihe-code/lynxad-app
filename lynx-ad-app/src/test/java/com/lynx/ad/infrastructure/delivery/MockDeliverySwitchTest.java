package com.lynx.ad.infrastructure.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.domain.port.OceanAdCreateCommand;
import com.lynx.ad.modules.delivery.infrastructure.adapter.MockDeliveryAdapters;
import com.lynx.ad.modules.identity.domain.model.Credential;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class MockDeliverySwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(Cfg.class, MockDeliveryAdapters.class);

    @Test
    void mockFalseDoesNotRegisterMockDelivery() {
        runner.withPropertyValues("lynx.ad.mock.qianchuan=false").run(ctx -> {
            assertFalse(ctx.containsBean("mockDeliveryAdapters"));
            assertFalse(ctx.getBeansOfType(MockDeliveryAdapters.class).size() > 0);
        });
    }

    @Test
    void mockTrueRegistersAndCreatesMockPrefix() {
        runner.withPropertyValues("lynx.ad.mock.qianchuan=true").run(ctx -> {
            MockDeliveryAdapters adapters = ctx.getBean(MockDeliveryAdapters.class);
            var id = adapters.create(
                    new Credential("t", new AdvertiserId("a")),
                    new OceanAdCreateCommand("p", "n", "SMART_BID_CONSERVATIVE", Money.ofYuan(300)));
            assertTrue(id.value().startsWith("mock_"));
        });
    }

    @Configuration
    @EnableConfigurationProperties(LynxAdProperties.class)
    static class Cfg {}
}
