package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.http.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.AssetUploadPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.nio.file.Path;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class QianchuanAssetUploadAdapter implements AssetUploadPort {
    private final QianchuanRestClient client;
    private final OceanJson json;
    private final LynxAdProperties properties;
    public QianchuanAssetUploadAdapter(QianchuanRestClient client, OceanJson json, LynxAdProperties properties) {
        this.client = client; this.json = json; this.properties = properties;
    }
    @Override public Result upload(Credential credential, AssetKind kind, Path file, String filename, String mime, String md5, boolean aigc) {
        if (kind == AssetKind.TEXT) throw new BizException(1520, "文本作为文案入库，无独立千川文件上传接口");
        com.lynx.ad.modules.creative.domain.service.AssetFilePolicy.target(filename,true);
        // Do not mark a fake platform upload as successful in local mock mode.
        checkAvailable();
        String prefix = kind.name().toLowerCase(Locale.ROOT);
        var api = properties.getQianchuan().getMaterial();
        String url = switch(kind) { case VIDEO -> api.getVideoUploadUrl(); case IMAGE -> api.getImageUploadUrl(); case AUDIO -> api.getAudioUploadUrl(); default -> throw new IllegalArgumentException(); };
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("advertiser_id", credential.advertiserId().value()); fields.put("upload_type", "UPLOAD_BY_FILE");
        fields.put(prefix + "_signature", md5);
        if (kind != AssetKind.AUDIO) { fields.put("filename", filename); fields.put("is_aigc", String.valueOf(aigc)); }
        String body = client.multipart(url, credential.accessToken(), fields, prefix + "_file", file, filename, mime);
        var root = json.root(body);
        int code = root.path("code").asInt(-1);
        if (code == 429) throw new BizException(429, "千川上传限流，请稍后重试");
        var data = json.dataOrThrow(body, 1511, "千川素材上传失败");
        if (kind == AssetKind.AUDIO) data = data.path("audio_info");
        String objectId = data.path(kind == AssetKind.IMAGE ? "id" : prefix + "_id").asText("");
        String materialId = data.path("material_id").asText("");
        if (objectId.isBlank()) throw new BizException(1523, "千川未返回完整素材标识，上传结果待确认");
        return new Result(objectId, materialId.isBlank()?null:materialId, root.path("request_id").asText(""));
    }
    @Override public void checkAvailable() {
        if (properties.getMock().isQianchuan()) throw new BizException(1524,"本地模拟模式未调用千川，切换真实模式后可重试同步");
    }
}
