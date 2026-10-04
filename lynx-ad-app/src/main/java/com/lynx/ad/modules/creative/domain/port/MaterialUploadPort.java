package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.identity.domain.model.Credential;

public interface MaterialUploadPort {

    String uploadVideo(TenantId tenantId, Credential credential, String objectKey, String filename);
}
