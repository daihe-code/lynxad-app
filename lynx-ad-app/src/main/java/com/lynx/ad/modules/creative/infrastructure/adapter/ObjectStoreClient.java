package com.lynx.ad.modules.creative.infrastructure.adapter;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStoreClient {

    void putObject(String bucket, String key, InputStream in, long size, String contentType);

    String presignedUrl(String bucket, String key, Duration ttl);

    void deleteObject(String bucket, String key);

    byte[] readObject(String bucket, String key);

    boolean exists(String bucket, String key);
}
