package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.List;

public interface StoragePort {

    StorageObject put(TenantId tenant, String objectKey, InputStream in, String contentType);

    String getUrl(TenantId tenant, String objectKey);

    void delete(TenantId tenant, String objectKey);

    byte[] read(TenantId tenant, String objectKey);

    default InputStream open(TenantId tenant, String key) { return new ByteArrayInputStream(read(tenant, key)); }
    default StorageObject put(TenantId tenant, String key, InputStream in, long size, String type) {
        return put(tenant, key, in, type);
    }
    /** Null means files must be streamed through the authenticated application endpoint. */
    default UploadTicket beginUpload(TenantId tenant, String key, long size, String type) { return null; }
    default void completeUpload(TenantId tenant, String key, String uploadId, List<UploadPart> parts, long size) {
        throw new UnsupportedOperationException("不支持直接上传");
    }
    default void abortUpload(TenantId tenant, String key, String uploadId) {}
    record UploadTicket(String uploadId, long partSize, List<String> urls) {}
    record UploadPart(int partNumber, String etag) {}
}
