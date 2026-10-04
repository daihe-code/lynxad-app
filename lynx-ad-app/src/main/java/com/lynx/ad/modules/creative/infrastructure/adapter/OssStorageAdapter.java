package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.port.StorageObject;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.domain.service.ObjectKeyPolicy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OssStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(OssStorageAdapter.class);

    private final LynxAdProperties properties;
    private final ObjectStoreClient client;

    public OssStorageAdapter(LynxAdProperties properties, ObjectStoreClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public StorageObject put(TenantId tenant, String objectKey, InputStream in, String contentType) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, objectKey);
        try {
            byte[] bytes = in.readAllBytes();
            client.putObject(bucket(), objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);
            log.info("oss put bucket={} keyPrefix={} size={}", bucket(), tenant.value(), bytes.length);
            return new StorageObject(objectKey, "oss", bytes.length, contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("对象存储写入失败", ex);
        }
    }

    @Override
    public String getUrl(TenantId tenant, String objectKey) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, objectKey);
        return client.presignedUrl(bucket(), objectKey, Duration.ofSeconds(Math.max(60, properties.getStorage().getUrlTtlSec())));
    }

    @Override
    public void delete(TenantId tenant, String objectKey) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, objectKey);
        client.deleteObject(bucket(), objectKey);
    }

    @Override
    public byte[] read(TenantId tenant, String objectKey) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, objectKey);
        byte[] data = client.readObject(bucket(), objectKey);
        if (data == null || data.length == 0) {
            throw new BizException(1508, "媒体令牌无效或过期");
        }
        return data;
    }

    private String bucket() {
        return properties.getStorage().getOss().getBucket();
    }

    private String prefix() {
        return properties.getStorage().getOss().getPrefix();
    }
}
