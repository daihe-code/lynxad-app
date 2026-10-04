package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.port.StorageObject;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.domain.service.MediaTokenSigner;
import com.lynx.ad.modules.creative.domain.service.ObjectKeyPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;

public class LocalStorageAdapter implements StoragePort {

    private final LynxAdProperties properties;
    private final String contextPath;

    public LocalStorageAdapter(
            LynxAdProperties properties, @Value("${server.servlet.context-path:/lynx-ad}") String contextPath) {
        this.properties = properties;
        this.contextPath = contextPath == null || contextPath.isBlank() ? "" : contextPath;
    }

    @Override
    public StorageObject put(TenantId tenant, String objectKey, InputStream in, String contentType) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, objectKey);
        try {
            Path target = resolve(objectKey);
            Files.createDirectories(target.getParent());
            long size = Files.copy(in, target);
            return new StorageObject(objectKey, "local", size, contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("本地存储写入失败", ex);
        }
    }

    @Override
    public String getUrl(TenantId tenant, String objectKey) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, objectKey);
        Instant exp = Instant.now().plusSeconds(Math.max(60, properties.getStorage().getUrlTtlSec()));
        String token = MediaTokenSigner.sign(properties.getCrypto().getAesKey(), tenant.value(), objectKey, exp);
        return contextPath + "/api/v1/creative/media?token=" + token;
    }

    @Override
    public void delete(TenantId tenant, String objectKey) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, objectKey);
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ex) {
            throw new IllegalStateException("本地存储删除失败", ex);
        }
    }

    @Override
    public byte[] read(TenantId tenant, String objectKey) {
        ObjectKeyPolicy.assertTenantKey(prefix(), tenant, objectKey);
        try {
            Path path = resolve(objectKey);
            if (!Files.exists(path)) {
                throw new BizException(1508, "媒体令牌无效或过期");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("本地存储读取失败", ex);
        }
    }

    private Path resolve(String objectKey) {
        return Path.of(properties.getStorage().getLocalDir()).resolve(objectKey).normalize();
    }

    private String prefix() {
        return properties.getStorage().getOss().getPrefix();
    }
}
