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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
            String filtering = "";
            if (query.type() != MaterialType.CAROUSEL) {
                String key = query.objectId() != null && !query.objectId().isBlank()
                        ? (query.type() == MaterialType.VIDEO ? "video_ids" : "image_ids") : "material_ids";
                String id = "material_ids".equals(key) ? query.materialId() : query.objectId();
                if (id != null && !id.isBlank()) {
                    var json = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                    if ("material_ids".equals(key)) json.putArray(key).add(new java.math.BigInteger(id));
                    else json.putArray(key).add(id);
                    filtering = "&filtering=" + URLEncoder.encode(json.toString(), StandardCharsets.UTF_8);
                }
            }
            String body = restClient.exchange(
                    RequestSpec.get(url + "?advertiser_id=" + (query.advertiserId() == null ? "" : query.advertiserId()) + "&page=" + query.page() + "&page_size=" + query.pageSize() + filtering)
                            .header("Access-Token", query.accessToken()==null ? "" : query.accessToken())
                            .build(),
                    String.class);
            JsonNode data = oceanJson.dataOrThrow(body, 1506, "素材库查询失败");
            JsonNode list = data.has("list") ? data.path("list") : data.path("materials");
            List<PlatformMaterial> items = new ArrayList<>();
            for (JsonNode n : list) {
                items.add(map(n, query.type()));
            }
            JsonNode info = data.path("page_info");
            Boolean hasMore = null;
            if (info.path("total_page").isIntegralNumber())
                hasMore = query.page() < info.path("total_page").asInt();
            else if (info.path("total_number").isIntegralNumber())
                hasMore = (long) query.page() * query.pageSize() < info.path("total_number").asLong();
            return new MaterialPage(items, hasMore);
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
        String id = n.path("material_id").asText(n.path("id").asText(""));
        Instant created = createdAt(n.path("create_time"));
        return new PlatformMaterial(
                n.path("object_id").asText(n.path(type==MaterialType.VIDEO ? "video_id" : "image_id").asText(n.path("id").asText(id))),
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

    @Override
    public java.util.Set<String> delete(DeleteQuery query) {
        if (query.type() == MaterialType.CAROUSEL || query.objectIds() == null || query.objectIds().isEmpty()
                || query.objectIds().size() > 100 || query.objectIds().stream().anyMatch(id -> id == null || id.isBlank()))
            throw new BizException(1520, "请选择 1-100 项视频或图片素材");
        var json = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        try { json.put("advertiser_id", new java.math.BigInteger(query.advertiserId())); }
        catch (RuntimeException invalid) { throw new BizException(1520, "广告账户信息不完整"); }
        boolean video = query.type() == MaterialType.VIDEO;
        var ids = json.putArray(video ? "video_ids" : "image_ids");
        query.objectIds().forEach(ids::add);
        String body = restClient.exchange(RequestSpec.post(video ? properties.getQianchuan().getVideoDeleteUrl()
                : properties.getQianchuan().getImageDeleteUrl()).header("Access-Token", query.accessToken())
                .replayAccountId(query.accountId()).json(json.toString()).build(), String.class);
        JsonNode data = oceanJson.dataOrThrow(body, 1523, "素材删除失败");
        // The documented failure list is optional; malformed receipts cannot confirm deletion.
        JsonNode failed = data.path(video ? "fail_video_ids" : "fail_image_ids");
        if (!data.isObject() || !failed.isMissingNode() && !failed.isArray())
            throw new BizException(1523, "删除结果暂未确认，请稍后重试");
        var result = new java.util.HashSet<String>();
        for (JsonNode id : failed) {
            if (!id.isTextual() || !query.objectIds().contains(id.asText()))
                throw new BizException(1523, "删除结果暂未确认，请稍后重试");
            result.add(id.asText());
        }
        return result;
    }

    private static Instant createdAt(JsonNode value) {
        if (!value.isNumber() && !value.isTextual()) return null;
        String raw = value.asText().trim();
        if (raw.matches("[0-9]{10}|[0-9]{13}")) {
            long epoch = Long.parseLong(raw);
            return epoch == 0 ? null : raw.length() == 13 ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
        }
        try { return OffsetDateTime.parse(raw).toInstant(); }
        catch (DateTimeParseException ignored) { }
        // Qianchuan video/image create_time is local China time, e.g. 2026-10-19 20:31:36.
        try { return LocalDateTime.parse(raw.replace(' ', 'T')).atZone(ZoneId.of("Asia/Shanghai")).toInstant(); }
        catch (DateTimeParseException ignored) { return null; }
    }
}
