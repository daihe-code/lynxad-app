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

public class OssStorageAdapter implements StoragePort, AutoCloseable {

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
        java.nio.file.Path temp = null;
        try {
            temp = java.nio.file.Files.createTempFile("lynx-storage-", ".tmp");
            long size = java.nio.file.Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (InputStream file = java.nio.file.Files.newInputStream(temp)) { return put(tenant, objectKey, file, size, contentType); }
        } catch (IOException ex) {
            throw new IllegalStateException("对象存储写入失败", ex);
        } finally {
            if (temp != null) try { java.nio.file.Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }

    @Override public StorageObject put(TenantId tenant, String key, InputStream in, long size, String type) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, key);
        client.putObject(bucket(), key, in, size, type);
        return new StorageObject(key, "oss", size, type);
    }
    @Override public InputStream open(TenantId tenant, String key) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, key); return client.openObject(bucket(), key);
    }
    @Override public StoragePort.UploadTicket beginUpload(TenantId tenant, String key, long size, String type) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, key);
        // Proxy by default: local browser uploads do not depend on bucket CORS/ETag settings.
        return properties.getStorage().isDirectUploadEnabled() ? client.beginUpload(bucket(), key, size, type) : null;
    }
    @Override public void completeUpload(TenantId tenant, String key, String uploadId, java.util.List<StoragePort.UploadPart> parts, long size) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, key); client.completeUpload(bucket(), key, uploadId, parts, size);
    }
    @Override public void abortUpload(TenantId tenant, String key, String uploadId) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, key); client.abortUpload(bucket(), key, uploadId);
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
    @Override public void close() throws Exception { if(client instanceof AutoCloseable closeable) closeable.close(); }
}
