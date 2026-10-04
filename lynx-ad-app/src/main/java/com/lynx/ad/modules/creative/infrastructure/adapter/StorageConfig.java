package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class StorageConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "lynx.ad.storage.mode", havingValue = "local", matchIfMissing = true)
    StoragePort localStorage(LynxAdProperties properties, @Value("${server.servlet.context-path:/lynx-ad}") String contextPath) {
        return new LocalStorageAdapter(properties, contextPath);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "lynx.ad.storage.mode", havingValue = "oss")
    StoragePort ossStorage(LynxAdProperties properties) {
        if (!properties.getStorage().isOssConfigured()) {
            return new UnconfiguredOssStorageAdapter();
        }
        return new OssStorageAdapter(properties, new AliyunOssClient(properties.getStorage().getOss()));
    }
}
