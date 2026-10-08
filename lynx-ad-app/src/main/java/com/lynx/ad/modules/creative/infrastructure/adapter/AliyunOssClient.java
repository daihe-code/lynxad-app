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
import java.util.ArrayList;
import java.util.List;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.*;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.common.exception.BizException;

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

    @Override public InputStream openObject(String bucket, String key) {
        OSSObject object = oss.getObject(bucket, key);
        return new java.io.FilterInputStream(object.getObjectContent()) {
            @Override public void close() throws IOException { try { super.close(); } finally { object.close(); } }
        };
    }

    @Override public StoragePort.UploadTicket beginUpload(String bucket, String key, long size, String type) {
        ObjectMetadata metadata = new ObjectMetadata(); metadata.setContentType(type);
        var request = new InitiateMultipartUploadRequest(bucket, key); request.setObjectMetadata(metadata);
        String uploadId = oss.initiateMultipartUpload(request).getUploadId();
        long partSize = 8L * 1024 * 1024;
        List<String> urls = new ArrayList<>();
        for (int part = 1; part <= (size + partSize - 1) / partSize; part++) {
            var sign = new GeneratePresignedUrlRequest(bucket, key, HttpMethod.PUT);
            sign.setExpiration(new Date(System.currentTimeMillis() + 3600_000));
            sign.addQueryParameter("uploadId", uploadId); sign.addQueryParameter("partNumber", String.valueOf(part));
            urls.add(oss.generatePresignedUrl(sign).toString());
        }
        return new StoragePort.UploadTicket(uploadId, partSize, urls);
    }

    @Override public void completeUpload(String bucket, String key, String uploadId, List<StoragePort.UploadPart> parts, long size) {
        PartListing listed;
        try { listed = oss.listParts(new ListPartsRequest(bucket, key, uploadId)); }
        catch (com.aliyun.oss.OSSException ex) {
            if ("NoSuchUpload".equals(ex.getErrorCode()) && oss.doesObjectExist(bucket,key) && oss.getObjectMetadata(bucket,key).getContentLength()==size) return;
            throw ex;
        }
        var actual = listed.getParts();
        if (listed.isTruncated() || actual.size() != parts.size() || actual.stream().mapToLong(PartSummary::getSize).sum() != size)
            throw new BizException(1521, "OSS 分片数量或文件大小不一致");
        List<PartETag> tags = new ArrayList<>();
        for (int index = 0; index < actual.size(); index++) {
            var a = actual.get(index); var p = parts.get(index);
            if (p.partNumber() != index + 1 || a.getPartNumber() != p.partNumber() || !a.getETag().equalsIgnoreCase(p.etag().replace("\"", "")))
                throw new BizException(1521, "OSS 分片校验失败");
            tags.add(new PartETag(a.getPartNumber(), a.getETag()));
        }
        oss.completeMultipartUpload(new CompleteMultipartUploadRequest(bucket, key, uploadId, tags));
    }
    @Override public void abortUpload(String bucket, String key, String uploadId) {
        oss.abortMultipartUpload(new AbortMultipartUploadRequest(bucket, key, uploadId));
    }

    @Override
    public void close() {
        oss.shutdown();
    }
}
