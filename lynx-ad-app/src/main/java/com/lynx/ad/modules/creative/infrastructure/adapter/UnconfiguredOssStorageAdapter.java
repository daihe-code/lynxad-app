package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.port.StorageObject;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import java.io.InputStream;

public class UnconfiguredOssStorageAdapter implements StoragePort {

    @Override
    public StorageObject put(TenantId tenant, String objectKey, InputStream in, String contentType) {
        throw new BizException(1507, "对象存储未配置");
    }

    @Override
    public String getUrl(TenantId tenant, String objectKey) {
        throw new BizException(1507, "对象存储未配置");
    }

    @Override
    public void delete(TenantId tenant, String objectKey) {
        throw new BizException(1507, "对象存储未配置");
    }

    @Override
    public byte[] read(TenantId tenant, String objectKey) {
        throw new BizException(1507, "对象存储未配置");
    }
}
