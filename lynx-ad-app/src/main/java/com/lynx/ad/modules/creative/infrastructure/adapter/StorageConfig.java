package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class StorageConfig {

    @Bean
    @Primary
    StoragePort storage(LynxAdProperties properties, @Value("${server.servlet.context-path:/lynx-ad}") String contextPath, EngineAssetStorage engine, com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository repository) {
        StoragePort oss=properties.getStorage().isOssConfigured()
                ? new OssStorageAdapter(properties,new AliyunOssClient(properties.getStorage().getOss()),repository)
                : new UnconfiguredOssStorageAdapter();
        engine.useObjectStorage(oss,properties);
        return new RoutingStorageAdapter(properties.getStorage().getMode(),new LocalStorageAdapter(properties,contextPath),oss,engine);
    }
}
