package com.lynx.ad.modules.creative.infrastructure.adapter;

import java.io.InputStream;
import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.util.List;
import com.lynx.ad.modules.creative.domain.port.StoragePort;

public interface ObjectStoreClient {

    record Metadata(long size,String contentType,String etag) {}
    default Metadata metadata(String bucket,String key){return null;}
    void putObject(String bucket, String key, InputStream in, long size, String contentType);

    String presignedUrl(String bucket, String key, Duration ttl);

    void deleteObject(String bucket, String key);

    byte[] readObject(String bucket, String key);

    boolean exists(String bucket, String key);
    default InputStream openObject(String bucket, String key) { return new ByteArrayInputStream(readObject(bucket, key)); }
    default StoragePort.UploadTicket beginUpload(String bucket, String key, long size, String type) { return null; }
    default void completeUpload(String bucket, String key, String uploadId, List<StoragePort.UploadPart> parts, long size) { throw new UnsupportedOperationException(); }
    default void abortUpload(String bucket, String key, String uploadId) {}
}
