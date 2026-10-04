package com.lynx.ad.modules.creative.infrastructure.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockOssClient implements ObjectStoreClient {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void putObject(String bucket, String key, InputStream in, long size, String contentType) {
        try {
            objects.put(bucket + "/" + key, in.readAllBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Mock OSS 写入失败", ex);
        }
    }

    @Override
    public String presignedUrl(String bucket, String key, Duration ttl) {
        return "https://mock-oss.local/" + bucket + "/" + key + "?ttl=" + ttl.toSeconds();
    }

    @Override
    public void deleteObject(String bucket, String key) {
        objects.remove(bucket + "/" + key);
    }

    @Override
    public byte[] readObject(String bucket, String key) {
        byte[] data = objects.get(bucket + "/" + key);
        return data == null ? new byte[0] : data;
    }

    @Override
    public boolean exists(String bucket, String key) {
        return objects.containsKey(bucket + "/" + key);
    }
}
