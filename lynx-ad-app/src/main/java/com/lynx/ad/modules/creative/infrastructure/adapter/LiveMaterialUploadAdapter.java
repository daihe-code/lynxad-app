package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.creative.domain.port.MaterialUploadPort;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "false")
public class LiveMaterialUploadAdapter implements MaterialUploadPort {

    private final QianchuanRestClient restClient;
    private final LynxAdProperties properties;
    private final OceanJson oceanJson;
    private final StoragePort storagePort;

    public LiveMaterialUploadAdapter(
            QianchuanRestClient restClient,
            LynxAdProperties properties,
            OceanJson oceanJson,
            StoragePort storagePort) {
        this.restClient = restClient;
        this.properties = properties;
        this.oceanJson = oceanJson;
        this.storagePort = storagePort;
    }

    @Override
    public String uploadVideo(TenantId tenantId, Credential credential, String objectKey, String filename) {
        byte[] bytes = storagePort.read(tenantId, objectKey);
        if (bytes == null || bytes.length == 0) {
            throw new BizException(1511, "本地素材不存在，无法上传");
        }
        try {
            String body = restClient.exchange(
                    RequestSpec.post(properties.getQianchuan().getMaterial().getVideoUploadUrl())
                            .header("Access-Token", credential.accessToken())
                            .json("{\"advertiser_id\":\""
                                    + credential.advertiserId().value()
                                    + "\",\"filename\":\""
                                    + filename
                                    + "\",\"video_file\":\""
                                    + Base64.getEncoder().encodeToString(bytes)
                                    + "\"}")
                            .build(),
                    String.class);
            JsonNode data = oceanJson.dataOrThrow(body, 1511, "素材上传失败");
            return data.path("video_id").asText(data.path("material_id").asText(""));
        } catch (BizException ex) {
            if (ex.code() == 1511) {
                throw ex;
            }
            throw new BizException(1511, "素材上传失败");
        }
    }
}
