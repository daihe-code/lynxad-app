package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.lynx.ad.common.config.LynxAdProperties;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Date;

public class AliyunOssClient implements ObjectStoreClient, AutoCloseable {

    private final OSS oss;

    public AliyunOssClient(LynxAdProperties.Oss ossProp) {
        this.oss = new OSSClientBuilder()
                .build(ossProp.getEndpoint(), ossProp.getAccessKeyId(), ossProp.getAccessKeySecret());
    }

    @Override
    public void putObject(String bucket, String key, InputStream in, long size, String contentType) {
        ObjectMetadata meta = new ObjectMetadata();
        if (size > 0) {
            meta.setContentLength(size);
        }
        if (contentType != null && !contentType.isBlank()) {
            meta.setContentType(contentType);
        }
        oss.putObject(bucket, key, in, meta);
    }

    @Override
    public String presignedUrl(String bucket, String key, Duration ttl) {
        Date exp = new Date(System.currentTimeMillis() + ttl.toMillis());
        return oss.generatePresignedUrl(bucket, key, exp).toString();
    }

    @Override
    public void deleteObject(String bucket, String key) {
        oss.deleteObject(bucket, key);
    }

    @Override
    public byte[] readObject(String bucket, String key) {
        try (OSSObject obj = oss.getObject(bucket, key); InputStream in = obj.getObjectContent()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("OSS 读取失败", ex);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        return oss.doesObjectExist(bucket, key);
    }

    @Override
    public void close() {
        oss.shutdown();
    }
}
