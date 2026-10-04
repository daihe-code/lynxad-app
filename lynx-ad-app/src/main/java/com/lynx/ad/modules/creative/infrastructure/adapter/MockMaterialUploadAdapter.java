package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.port.MaterialUploadPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "true", matchIfMissing = true)
public class MockMaterialUploadAdapter implements MaterialUploadPort {

    @Override
    public String uploadVideo(TenantId tenantId, Credential credential, String objectKey, String filename) {
        return "mock_video_" + filename;
    }
}
