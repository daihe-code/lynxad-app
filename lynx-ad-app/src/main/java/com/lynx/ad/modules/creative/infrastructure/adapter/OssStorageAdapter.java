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
    private final com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository repository;

    public OssStorageAdapter(LynxAdProperties properties, ObjectStoreClient client) {
        this(properties,client,null);
    }
    public OssStorageAdapter(LynxAdProperties properties,ObjectStoreClient client,com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository repository) {
        this.repository=repository;
        this.properties = properties;
        this.client = client;
    }

    @Override
    public StorageObject put(TenantId tenant, String objectKey, InputStream in, String contentType) {
        assertKey(tenant,objectKey);
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
        assertKey(tenant,key);
        client.putObject(bucket(tenant,key), key, in, size, type);
        recordEtag(tenant,key);
        return new StorageObject(key, "oss", size, type);
    }
    @Override public InputStream open(TenantId tenant, String key) {
        assertKey(tenant,key); return client.openObject(bucket(tenant,key), key);
    }
    @Override public StoragePort.UploadTicket beginUpload(TenantId tenant, String key, long size, String type) {
        assertKey(tenant,key);
        // Proxy by default: local browser uploads do not depend on bucket CORS/ETag settings.
        return properties.getStorage().isDirectUploadEnabled() ? client.beginUpload(bucket(tenant,key), key, size, type) : null;
    }
    @Override public void completeUpload(TenantId tenant, String key, String uploadId, java.util.List<StoragePort.UploadPart> parts, long size) {
        assertKey(tenant,key); client.completeUpload(bucket(tenant,key), key, uploadId, parts, size); recordEtag(tenant,key);
    }
    @Override public void abortUpload(TenantId tenant, String key, String uploadId) {
        assertKey(tenant,key); client.abortUpload(bucket(tenant,key), key, uploadId);
    }

    @Override
    public String getUrl(TenantId tenant, String objectKey) {
        assertKey(tenant,objectKey);
        return client.presignedUrl(bucket(tenant,objectKey), objectKey, Duration.ofSeconds(Math.max(60, properties.getStorage().getUrlTtlSec())));
    }

    @Override
    public void delete(TenantId tenant, String objectKey) {
        assertKey(tenant,objectKey);
        client.deleteObject(bucket(tenant,objectKey), objectKey);
    }

    @Override
    public byte[] read(TenantId tenant, String objectKey) {
        assertKey(tenant,objectKey);
        byte[] data = client.readObject(bucket(tenant,objectKey), objectKey);
        if (data == null || data.length == 0) {
            throw new BizException(1508, "媒体令牌无效或过期");
        }
        return data;
    }

    private void recordEtag(TenantId tenant,String key) {
        if(repository==null)return;
        var meta=client.metadata(bucket(tenant,key),key);
        if(meta!=null)repository.storageObject(tenant.value(),key).ifPresent(a->repository.etag(tenant.value(),a.id(),meta.etag()));
    }
    private void assertKey(TenantId tenant,String key) {
        if(repository!=null) {
            var asset=repository.storageObject(tenant.value(),key).orElse(null);
            if(asset!=null && repository.isEngineAsset(tenant.value(),asset.id()))return;
        }
        ObjectKeyPolicy.assertTenantKey(prefix(),tenant,key);
    }
    private String bucket(TenantId tenant,String key) {
        var config=properties.getStorage().getOss();
        if(repository!=null) {
            var asset=repository.storageObject(tenant.value(),key).orElse(null);
            var meta=asset==null?null:repository.storageMetadata(tenant.value(),asset.id());
            if(meta!=null&&meta.bucket()!=null&&!meta.bucket().isBlank()) {
                if(meta.endpoint()!=null&&!meta.endpoint().isBlank()&&!meta.endpoint().replace("https://","").replace("http://","").replaceAll("/+$","").equals(config.getEndpoint().replace("https://","").replace("http://","").replaceAll("/+$","")))throw new BizException(1521,"素材存储区域与当前配置不一致");
                return meta.bucket();
            }
        }
        if(key!=null&&key.startsWith("lynx-ad/v2/"))return com.lynx.ad.modules.creative.domain.service.AssetStoragePolicy.bucket(config,com.lynx.ad.modules.creative.domain.service.AssetFilePolicy.spec(key).kind().name());
        return config.getBucket();
    }

    private String prefix() {
        return properties.getStorage().getOss().getPrefix();
    }
    @Override public void close() throws Exception { if(client instanceof AutoCloseable closeable) closeable.close(); }
}
