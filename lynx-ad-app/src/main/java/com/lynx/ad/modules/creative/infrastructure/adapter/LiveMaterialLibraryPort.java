package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.common.http.RequestSpec;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lynx.ad.mock.qianchuan", havingValue = "false")
public class LiveMaterialLibraryPort implements MaterialLibraryPort {

    private final QianchuanRestClient restClient;
    private final LynxAdProperties properties;
    private final OceanJson oceanJson;

    public LiveMaterialLibraryPort(QianchuanRestClient restClient, LynxAdProperties properties, OceanJson oceanJson) {
        this.restClient = restClient;
        this.properties = properties;
        this.oceanJson = oceanJson;
    }

    @Override
    public MaterialPage list(MaterialLibraryQuery query) {
        try {
            String url = switch (query.type()) {
                case VIDEO -> properties.getQianchuan().getVideoGetUrl();
                case IMAGE -> properties.getQianchuan().getImageGetUrl();
                case CAROUSEL -> properties.getQianchuan().getCarouselGetUrl();
            };
            String body = restClient.exchange(
                    RequestSpec.get(url + "?advertiser_id=" + (query.advertiserId() == null ? "" : query.advertiserId()))
                            .build(),
                    String.class);
            JsonNode data = oceanJson.dataOrThrow(body, 1506, "素材库查询失败");
            JsonNode list = data.has("list") ? data.path("list") : data.path("materials");
            List<PlatformMaterial> items = new ArrayList<>();
            for (JsonNode n : list) {
                items.add(map(n, query.type()));
            }
            return new MaterialPage(items);
        } catch (BizException ex) {
            if (ex.code() == 1506) {
                throw ex;
            }
            throw new BizException(1506, "素材库查询失败");
        } catch (RuntimeException ex) {
            throw new BizException(1506, "素材库查询失败");
        }
    }

    private static PlatformMaterial map(JsonNode n, MaterialType type) {
        String id = n.path("material_id").asText(n.path("id").asText("ext"));
        Instant created = n.has("create_time")
                ? Instant.ofEpochSecond(n.path("create_time").asLong(Instant.now().getEpochSecond()))
                : Instant.now();
        return new PlatformMaterial(
                n.path("object_id").asText(id),
                id,
                n.path("filename").asText(n.path("name").asText(type.name() + ".bin")),
                n.path("size").asLong(0),
                n.has("width") ? n.path("width").asInt() : null,
                n.has("height") ? n.path("height").asInt() : null,
                n.has("duration") ? n.path("duration").asInt() : null,
                n.path("image_mode").asText(type == MaterialType.VIDEO ? "VIDEO_VERTICAL" : "SQUARE"),
                "EXTERNAL",
                created,
                n.path("url").asText(""),
                n.path("poster_url").asText(n.path("url").asText("")),
                n.path("is_ai_create").asBoolean(false),
                n.path("review_badge").asText("NONE"));
    }
}
