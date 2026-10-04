package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import java.io.InputStream;

public interface StoragePort {

    StorageObject put(TenantId tenant, String objectKey, InputStream in, String contentType);

    String getUrl(TenantId tenant, String objectKey);

    void delete(TenantId tenant, String objectKey);

    byte[] read(TenantId tenant, String objectKey);
}
