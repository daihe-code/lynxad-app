package com.lynx.ad.modules.creative.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.GenerationCharges;
import com.lynx.ad.modules.billing.application.WorkspacePricing;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient.RemakeEngineError;
import com.lynx.ad.modules.creative.infrastructure.adapter.DouyinLinkVideoDownloader;
import com.lynx.ad.modules.creative.infrastructure.adapter.LinkImportFailure;
import com.lynx.ad.modules.creative.infrastructure.persistence.VideoRemakeStore;
import com.lynx.ad.modules.creative.infrastructure.persistence.VideoRemakeStore.Task;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.identity.application.AccountBindingRegistry;
import com.lynx.ad.modules.tenant.application.WorkspaceAccess;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VideoRemakeService {
    private static final Logger LOG = LoggerFactory.getLogger(VideoRemakeService.class);
    public static final int CHUNK_BYTES = 1024 * 1024;

    private final CreationEngineClient engine;
    private final CreationQianchuanGateway catalog;
    private final AccountBindingRegistry bindings;
    private final WorkspaceAccess access;
    private final GenerationCharges charges;
    private final WorkspacePricing pricing;
    private final VideoRemakeStore store;
    private final CreationAssetImporter importer;
    private final AssetUploadService assets;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    @Value("${lynx.ad.remake-link.download-api-base-url:}")
    private String downloadApiBaseUrl = "";
    @Value("${lynx.ad.remake-link.download-api-path:}")
    private String downloadApiPath = "";
    @Value("${lynx.ad.remake-link.download-api-key:}")
    private String downloadApiKey = "";
    @Value("${lynx.ad.remake-link.ffprobe-path:ffprobe}")
    private String ffprobePath = "ffprobe";

    public VideoRemakeService(CreationEngineClient engine, CreationQianchuanGateway catalog,
            AccountBindingRegistry bindings, WorkspaceAccess access, GenerationCharges charges,
            WorkspacePricing pricing, VideoRemakeStore store, CreationAssetImporter importer,
            AssetUploadService assets, TransactionTemplate tx, ObjectMapper json) {
        this.engine = engine;
        this.catalog = catalog;
        this.bindings = bindings;
        this.access = access;
        this.charges = charges;
        this.pricing = pricing;
        this.store = store;
        this.importer = importer;
        this.assets = assets;
        this.tx = tx;
        this.json = json;
    }

    public record Capability(boolean ready, boolean subscribed, String balanceStatus, BigDecimal points,
                             boolean kling4Available, BigDecimal availablePoints, boolean sourceMatchingHd,
                             boolean sourceMatchingAvailable, boolean sourcePeopleSupported, boolean productPeopleSupported,
                             Map<String, BigDecimal> pointsPerSecond) {}
    public record Upload(String requestId, String mediaType, String filename, long size, String sha256) {}
    public record LinkImport(String requestId, String url) {}
    @JsonIgnoreProperties({"facts", "productImageId", "productImageIds", "productImages", "adNotices"})
    public record Start(String requestId, String bindingId, String awemeId, String productId,
                        String referenceId, String portraitId, String portraitName,
                        boolean rightsConfirmed, boolean portraitTermsConfirmed, int durationSeconds,
                        String portraitImageId, boolean followSource, boolean preserveSourcePeople, String productPeopleMode) {}
    public record ShotEdit(String visualPrompt, String narration, String caption) {}
    public record Confirm(String title, List<ShotEdit> shots, boolean accepted, BigDecimal points) {}
    public record RemakeQuote(BigDecimal points, BigDecimal pointsPerSecond) {}
    public record View(String id, String product, String phase, String stage, String error, Instant createdAt,
                       String assetId, JsonNode plan, String videoUrl, String coverUrl, List<String> adNotices,
                       JsonNode outputSpec, RemakeQuote quote, List<String> auditIssues, List<String> auditWarnings,
                       List<String> productImageUrls, List<String> consistencyIssues, List<String> generationWarnings,
                       List<VideoRemakeGeneration.ShotRetry> shotRetries, List<String> progressSteps) {}

    private record RemakeCharge(String kind, int quantity, BigDecimal rate, int images, BigDecimal imageRate) {
        Map<String, Integer> units() {
            return images > 0 ? Map.of(kind, quantity, WorkspacePricing.IMAGE_UNIT, images) : Map.of(kind, quantity);
        }

        RemakeQuote quote() {
            var total = rate.multiply(BigDecimal.valueOf(quantity)).add(imageRate.multiply(BigDecimal.valueOf(images)));
            return new RemakeQuote(total, "video".equals(kind) ? null : rate);
        }
    }

    public Capability capability(String tenant) {
        var membership = charges.access(tenant);
        var rate = pricing.rate("video");
        if (!membership.enabled()) return new Capability(false, false, null, rate, false, null, false, false, false, false, Map.of());
        var pointsPerSecond = Map.of("720p", pricing.rate(WorkspacePricing.REMAKE_720P_SECOND),
                "1080p", pricing.rate(WorkspacePricing.REMAKE_1080P_SECOND));
        try {
            var status = engine.remake("GET", "/capabilities", null);
            return new Capability(status.path("ready").asBoolean() && status.path("review_worker_ready").asBoolean(),
                    true, membership.balanceStatus().name(), rate, status.path("kling_4_available").asBoolean(),
                    membership.availablePoints(), status.path("source_matching_hd").asBoolean(),
                    status.path("source_matching_available").asBoolean(), status.path("source_people_supported").asBoolean(),
                    status.path("product_people_supported").asBoolean(), pointsPerSecond);
        } catch (RemakeEngineError ex) {
            return new Capability(false, true, membership.balanceStatus().name(), rate, false,
                    membership.availablePoints(), false, false, false, false, pointsPerSecond);
        }
    }

    public Map<String, Object> upload(String tenant, String actor, String binding, Upload request) {
        requireGeneration(tenant, actor, binding);
        boolean video = "video".equals(request.mediaType());
        if (!video && !"image".equals(request.mediaType())) throw input("请选择参考视频或商品图片");
        String filename = text(request.filename(), 150);
        if (!filename.toLowerCase(java.util.Locale.ROOT).matches(video ? ".*\\.(mp4|mov|webm)" : ".*\\.(jpg|jpeg|png|webp)")
                || request.size() <= 0 || request.size() > (video ? 200L : 10L) * CHUNK_BYTES
                || request.sha256() == null || !request.sha256().matches("[a-f0-9]{64}")) {
            throw input("素材格式、大小或文件校验值不符合要求");
        }
        String key = UUID.nameUUIDFromBytes((actor + ":" + binding + ":" + uuid(request.requestId()))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var result = engine.remake("POST", "/uploads", Map.of("store_id", tenant, "request_id", key,
                "filename", filename, "size", request.size(), "sha256", request.sha256(), "media_type", request.mediaType()));
        String id = identifier(result.path("id").asText(), "rpu_");
        store.recordUpload(tenant, actor, binding, id, request.mediaType());
        return Map.of("id", id, "chunkBytes", CHUNK_BYTES);
    }

    public void chunk(String tenant, String actor, String binding, String id, int index, byte[] data) {
        requireGeneration(tenant, actor, binding);
        if (data.length == 0 || data.length > CHUNK_BYTES || index < 0 || index >= 200) throw input("素材分片无效");
        requireUpload(tenant, actor, binding, id);
        engine.remake("PUT", "/uploads/" + id + "/chunks/" + index + scope(tenant), data);
    }

    public String finishUpload(String tenant, String actor, String binding, String id) {
        requireGeneration(tenant, actor, binding);
        requireUpload(tenant, actor, binding, id);
        var result = engine.remake("POST", "/uploads/" + id + "/finish" + scope(tenant), null);
        String asset = identifier(result.path("id").asText(), "rpa_");
        store.finishUpload(tenant, id, asset);
        return asset;
    }

    /**
     * 从抖音链接导入参考视频：服务端下载并用 ffprobe 校验后，走与本地上传相同的引擎上传
     * （开始上传、分片、完成）与素材登记；下载失败时直接返回明确错误，不生成复刻任务。
     */
    public Map<String, Object> importLink(String tenant, String actor, String binding, LinkImport request) {
        requireGeneration(tenant, actor, binding);
        String requestId = uuid(request.requestId());
        DouyinLinkVideoDownloader.DownloadedVideo downloaded;
        try {
            downloaded = DouyinLinkVideoDownloader.download(json, request.url(), downloadApiBaseUrl,
                    downloadApiPath, downloadApiKey, ffprobePath);
        } catch (LinkImportFailure ex) {
            LOG.warn("link import failed tenant={} binding={} reason={} detail={} awemeId={}",
                    tenant, binding, ex.reason(), ex.detail(), ex.awemeId());
            throw ex;
        }
        Path file = downloaded.file();
        try {
            var initiated = upload(tenant, actor, binding,
                    new Upload(requestId, "video", downloaded.filename(), downloaded.size(),
                            downloaded.sha256Hex()));
            String id = String.valueOf(initiated.get("id"));
            store.recordUploadSource(tenant, id, downloaded.sourceUrl(), downloaded.platform(),
                    downloaded.downloadMethod());
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[CHUNK_BYTES];
                int index = 0;
                int read;
                while ((read = in.readNBytes(buffer, 0, buffer.length)) > 0) {
                    byte[] data = read == buffer.length ? buffer : Arrays.copyOf(buffer, read);
                    chunk(tenant, actor, binding, id, index, data);
                    index++;
                }
            } catch (IOException ex) {
                throw new BizException(1402, "链接视频上传到引擎失败，请稍后重试。");
            }
            String asset = finishUpload(tenant, actor, binding, id);
            return Map.of("assetId", asset, "durationSeconds", downloaded.probe().durationSeconds(),
                    "width", downloaded.probe().width(), "height", downloaded.probe().height(),
                    "sourceUrl", downloaded.sourceUrl(), "sourcePlatform", downloaded.platform(),
                    "downloadMethod", downloaded.downloadMethod());
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // 临时文件删除失败不影响已完成的入库结果。
            }
        }
    }

    public View create(String tenant, String actor, Start request) {
        String id = uuid(request.requestId());
        var existing = store.find(tenant, id, false);
        if (existing.isPresent()) return summary(owned(tenant, actor, id));
        requireGeneration(tenant, actor, request.bindingId());
        if (!request.rightsConfirmed()) throw input("请确认参考视频的使用授权");
        String productPeopleMode = request.productPeopleMode();
        if (productPeopleMode != null && !Set.of("auto", "none").contains(productPeopleMode)) {
            throw input("请选择有效的人物设置");
        }
        boolean skipPortrait = productPeopleMode != null || request.preserveSourcePeople();
        if (!skipPortrait && !request.portraitTermsConfirmed()) throw input("请确认替换人像的使用授权");
        if ((productPeopleMode != null && request.preserveSourcePeople()) || (skipPortrait
                && Stream.of(request.portraitId(), request.portraitName(), request.portraitImageId())
                .anyMatch(value -> value != null && !value.isBlank()))) throw input("请只选择一种人物设置");
        if (!request.followSource() && !Set.of(15, 30).contains(request.durationSeconds())) throw input("请选择 15 秒或 30 秒");
        String productId = text(request.productId(), 19);
        var product = catalog.restorable(new TenantId(tenant), request.bindingId(), text(request.awemeId(), 128), List.of(productId))
                .stream().filter(row -> productId.equals(row.qcProductId())).findFirst()
                .orElseThrow(() -> input("所选商品已不可用，请重新选择"));
        String productName = text(product.name(), 200);
        var target = json.createObjectNode().put("id", productId).put("name", productName);
        // The engine requires a verified fact. The authorized catalog supplies the exact product name.
        target.putArray("facts").add(productName);
        if (product.category() != null && !product.category().isBlank()) target.put("category", product.category().strip());
        var productImages = Stream.concat(Stream.ofNullable(product.imageUrl()), product.imageUrls().stream())
                .filter(java.util.Objects::nonNull)
                .map(String::strip).filter(VideoRemakeService::isProductImageUrl).distinct().limit(6).toList();
        if (productImages.isEmpty()) throw input("所选商品没有可用的商品图，请重新选择");
        var imageUrls = target.putArray("image_urls");
        productImages.forEach(imageUrls::add);
        var payload = json.createObjectNode().put("store_id", tenant).put("request_id", id)
                .put("source_material_id", requireAsset(tenant, actor, request.bindingId(), request.referenceId(), "video"))
                .put("max_shots", 4).put("rights_confirmed", true).put("costs_confirmed", true)
                .put("require_plan_confirmation", true);
        if (request.followSource() || skipPortrait) {
            var capability = engine.remake("GET", "/capabilities", null);
            if (request.followSource() && !capability.path("source_matching_available").asBoolean()) {
                throw input("复刻服务暂不可用，请稍后重试");
            }
            if (request.preserveSourcePeople() && !capability.path("source_people_supported").asBoolean()) {
                throw input("当前复刻服务暂时需要替换人物");
            }
            if (productPeopleMode != null && !capability.path("product_people_supported").asBoolean()) {
                throw input("当前复刻服务暂时需要替换人物");
            }
        }
        if (request.followSource()) {
            payload.put("orientation", "source").put("follow_source", true);
        } else {
            payload.put("orientation", "portrait").put("duration_seconds", request.durationSeconds())
                    .put("_charge_by_second", true);
        }
        payload.set("product", target);
        if (productPeopleMode != null) {
            payload.put("product_people_mode", productPeopleMode);
        } else if (request.preserveSourcePeople()) {
            payload.put("preserve_source_people", true);
        } else if (request.portraitImageId() != null && !request.portraitImageId().isBlank()) {
            if (request.portraitId() != null && !request.portraitId().isBlank()) {
                throw input("人物参考图和方舟预置人像只能选一种");
            }
            String actorImage = requireAsset(tenant, actor, request.bindingId(), request.portraitImageId(), "image");
            payload.putObject("kling_portrait").put("image_asset_id", actorImage)
                    .put("rights_confirmed", true).put("adult_confirmed", true);
        } else {
            String portrait = text(request.portraitId(), 136).replaceFirst("^asset://", "");
            if (!portrait.matches("asset-[A-Za-z0-9_-]{4,120}")) throw input("请填写有效的方舟预置人像 ID");
            payload.putObject("portrait").put("source", "ark_preset").put("asset_id", portrait)
                    .put("label", text(request.portraitName(), 80))
                    .put("platform_terms_confirmed", true).put("adult_confirmed", true);
        }
        return tx.execute(status -> {
            store.lockWorkspace(tenant);
            var previous = store.find(tenant, id, false);
            if (previous.isPresent()) return summary(owned(tenant, actor, id));
            if (store.pending(tenant, actor) >= 3) throw input("当前有 3 个复刻任务正在处理");
            if (store.recent(tenant, actor) >= 10) throw input("近 24 小时的复刻分析次数已用完");
            store.create(tenant, id, actor, request.bindingId(), productName, payload.toString());
            return summary(owned(tenant, actor, id));
        });
    }

    public List<View> history(String tenant, String actor) {
        return store.history(tenant, actor).stream().filter(task -> access.canReadBinding(tenant, task.binding()))
                .map(this::summary).toList();
    }

    public View get(String tenant, String actor, String id) {
        var task = owned(tenant, actor, id);
        if (task.engine() == null) return summary(task);
        if ("FAILED".equals(task.phase())) return failedView(task);
        if (!Set.of("REVIEW", "SUCCEEDED", "GENERATING", "PLANNING").contains(task.phase())) return summary(task);
        var data = detail(task);
        var result = data.path("delivery");
        boolean finished = "SUCCEEDED".equals(task.phase());
        var adNotices = data.path("ad_notices").valueStream().filter(JsonNode::isTextual).map(JsonNode::asText).toList();
        RemakeQuote quote = null;
        if ("REVIEW".equals(task.phase())) {
            var charge = remakeCharge(task, data);
            if (charge != null) {
                quote = charge.quote();
                charges.access(tenant, quote.points());
            }
        } else if (task.confirmation() != null && (finished || "GENERATING".equals(task.phase()))) {
            var reserved = charges.quote(tenant, chargeKey(task));
            if (reserved != null) quote = new RemakeQuote(reserved.total(), null);
        }
        return new View(task.id(), task.product(), task.phase(), data.path("stage").asText(null), task.error(),
                task.createdAt(), task.asset(), data.get("plan"), finished ? result.path("video_url").asText(null) : null,
                finished ? result.path("cover_url").asText(null) : null, adNotices, data.get("output_spec"),
                quote, auditIssues(data), auditWarnings(data), productImageUrls(task), consistencyIssues(data),
                VideoRemakeGeneration.generationWarnings(data), VideoRemakeGeneration.shotRetries(data),
                VideoRemakeGeneration.progressSteps(data));
    }

    public View confirm(String tenant, String actor, String id, Confirm request) {
        var task = owned(tenant, actor, id);
        if (!"REVIEW".equals(task.phase())) return summary(task);
        if (!request.accepted() || request.points() == null)
            throw input("请重新核对分镜及本次生成所需积分");
        var data = detail(task);
        var charge = remakeCharge(task, data);
        if (charge == null) throw input("成片规格尚未确认，请刷新后再试");
        if (charge.quote().points().compareTo(request.points()) != 0)
            throw input("生成积分已变化，请刷新后再确认");
        var plan = data.path("plan");
        if (!plan.isObject() || !plan.path("shots").isArray() || request.shots() == null
                || plan.path("shots").size() != request.shots().size()) throw input("分镜已变化，请刷新后再确认");
        var edited = (ObjectNode) plan.deepCopy();
        edited.put("title", text(request.title(), 100));
        for (int i = 0; i < request.shots().size(); i++) {
            var shot = request.shots().get(i);
            if (shot == null) throw input("请填写完整分镜");
            var old = (ObjectNode) edited.path("shots").get(i);
            old.put("visual_prompt", text(shot.visualPrompt(), 10, 1800));
            old.put("narration", text(shot.narration(), Math.min(120, old.path("duration").asInt() * 5)));
            old.put("caption", text(shot.caption(), 24));
        }
        var payload = json.createObjectNode().put("store_id", tenant).put("request_id", id);
        payload.set("plan", edited);
        return tx.execute(status -> {
            store.lockWorkspace(tenant);
            var locked = store.find(tenant, id, true).orElseThrow();
            if (!"REVIEW".equals(locked.phase())) return summary(locked);
            if (store.pending(tenant, actor) >= 3) throw input("当前有 3 个复刻任务正在处理");
            charges.hold(tenant, chargeKey(locked), locked.binding(), actor, charge.units());
            var quote = charges.quote(tenant, chargeKey(locked));
            if (quote == null || !quote.requested().equals(charge.units()) || quote.total().compareTo(request.points()) != 0)
                throw input("生成积分已变化，请刷新后再确认");
            store.queueConfirmation(locked, payload.toString());
            return summary(store.find(tenant, id, false).orElseThrow());
        });
    }

    private RemakeCharge remakeCharge(Task task, JsonNode data) {
        var request = parse(task.request());
        if (!request.path("follow_source").asBoolean()) {
            if (request.path("_charge_by_second").asBoolean()) {
                var kind = WorkspacePricing.REMAKE_720P_SECOND;
                return new RemakeCharge(kind, request.path("duration_seconds").asInt(), pricing.rate(kind), 0, BigDecimal.ZERO);
            }
            return new RemakeCharge("video", 1, pricing.rate("video"), 0, BigDecimal.ZERO);
        }
        var spec = data.path("output_spec");
        var width = spec.path("width");
        var height = spec.path("height");
        var duration = spec.path("duration_seconds");
        if (!width.canConvertToExactIntegral() || !height.canConvertToExactIntegral()
                || !width.canConvertToInt() || !height.canConvertToInt()
                || !duration.isIntegralNumber() || !duration.canConvertToInt()
                || duration.intValue() < 4 || duration.intValue() > 120) return null;
        int shortEdge = Math.min(width.intValue(), height.intValue()), longEdge = Math.max(width.intValue(), height.intValue());
        String kind = switch (shortEdge) {
            case 720 -> longEdge == 1280 ? WorkspacePricing.REMAKE_720P_SECOND : null;
            case 1080 -> longEdge == 1920 ? WorkspacePricing.REMAKE_1080P_SECOND : null;
            default -> null;
        };
        if (kind == null) return null;
        // Older engine tasks have no image breakdown and retain their existing video quote.
        var breakdown = data.path("points_breakdown");
        int keyframes = Math.max(0, breakdown.path("keyframes").asInt(0));
        int shotImages = Math.max(0, breakdown.path("shot_images").asInt(0));
        var charge = new RemakeCharge(kind, duration.intValue(), pricing.rate(kind), keyframes + shotImages,
                pricing.rate(WorkspacePricing.IMAGE_UNIT));
        LOG.info("remake charge task={} scenes={} keyframes={} shot_images={} shots={} video_seconds={} units={} points={}",
                task.id(), breakdown.path("scenes").asInt(0), keyframes, shotImages, breakdown.path("shots").asInt(0),
                duration.intValue(), charge.units(), charge.quote().points());
        return charge;
    }

    public String save(String tenant, String actor, String id) {
        var task = owned(tenant, actor, id);
        if (task.asset() != null) return task.asset();
        if (!"SUCCEEDED".equals(task.phase())) throw input("成片尚未就绪");
        var scope = bindings.executionScope(tenant, task.binding(), actor);
        var owner = new TenantId(tenant);
        var user = new UserId(actor);
        var cover = importer.reference(owner, user, "remake:" + task.engine() + ":cover",
                task.engine(), task.engine(), "COVER", "IMAGE", null, true);
        var video = importer.reference(owner, user, "remake:" + task.engine() + ":video",
                task.engine(), task.engine(), "VIDEO", "VIDEO", null, false);
        bindings.validate(scope);
        assets.attachPreview(owner, video, cover);
        assets.ensureTarget(owner, video, task.binding());
        store.saved(task, video);
        return video;
    }

    @Scheduled(fixedDelay = 3000, scheduler = "videoRemakeScheduler")
    public void advance() {
        for (var task : store.due()) {
            String token = UUID.randomUUID().toString();
            if (!store.claim(task, token)) continue;
            try {
                advance(task, token);
            } catch (RemakeEngineError ex) {
                if ("CREATING".equals(task.phase()) && ex.inputRejected() && ex.status() != 404) {
                    store.update(task, token, "FAILED", task.engine(), ex.getMessage(), 0);
                } else {
                    store.update(task, token, task.phase(), task.engine(), ex.getMessage(), 30);
                }
            } catch (RuntimeException ex) {
                LOG.warn("Video remake checkpoint unavailable for task {} ({})", task.id(), ex.getClass().getSimpleName());
                store.update(task, token, task.phase(), task.engine(), "暂未取得最新进度，系统会重试", 30);
            }
        }
    }

    private void advance(Task task, String token) {
        if ("CREATING".equals(task.phase())) {
            var payload = (ObjectNode) parse(task.request());
            payload.remove("_charge_by_second");
            var reply = engine.remake("POST", "/tasks", payload);
            if (!task.tenant().equals(reply.path("store_id").asText())) throw input("复刻任务归属不匹配");
            store.update(task, token, "PLANNING", identifier(reply.path("id").asText(), "rpt_"), null, 3);
            return;
        }
        if ("CONFIRMING".equals(task.phase())) {
            try {
                charges.validate(task.tenant(), chargeKey(task));
            } catch (BizException ex) {
                complete(task, token, "FAILED", ex.getMessage(), true, false);
                return;
            }
            store.update(task, token, "SENDING", task.engine(), null, 0);
            return;
        }
        var current = detail(task);
        String remote = current.path("status").asText();
        if ("awaiting_confirmation".equals(remote) && "SENDING".equals(task.phase())) {
            dispatch(task, token);
            return;
        }
        boolean paid = !Set.of("PLANNING", "REVIEW").contains(task.phase());
        switch (remote) {
            case "awaiting_confirmation" -> {
                if (!"PLANNING".equals(task.phase()) && !"REVIEW".equals(task.phase())) throw input("引擎确认记录待核对");
                autoConfirm(task, token, current);
            }
            case "succeeded" -> {
                if (!paid || !current.path("delivery").path("typography_passed").asBoolean()
                        || current.path("delivery").path("video_url").asText().isBlank()
                        || current.path("delivery").path("cover_url").asText().isBlank())
                    throw input("成片尚未通过交付核对");
                complete(task, token, "SUCCEEDED", null, true, true);
            }
            case "failed" -> complete(task, token, "FAILED", error(current, "复刻任务执行失败"), paid, false);
            case "needs_attention" -> store.update(task, token, paid ? "NEEDS_ATTENTION" : task.phase(), task.engine(),
                    error(current, paid ? "生成结果待核对，积分仍在预占中" : "分析结果待核对"), 60);
            case "review_queued", "review_processing" -> store.update(task, token,
                    paid ? "GENERATING" : "PLANNING", task.engine(), error(current, null), 8);
            default -> throw input("复刻任务状态待核对");
        }
    }

    private void autoConfirm(Task task, String token, JsonNode data) {
        var charge = remakeCharge(task, data);
        var plan = data.path("plan");
        if (charge == null || !plan.isObject() || !plan.path("shots").isArray() || plan.path("shots").isEmpty()) {
            store.update(task, token, task.phase(), task.engine(), null, 8);
            return;
        }
        var payload = json.createObjectNode().put("store_id", task.tenant()).put("request_id", task.id());
        payload.set("plan", plan);
        try {
            var queued = tx.execute(status -> {
                store.lockWorkspace(task.tenant());
                var locked = store.find(task.tenant(), task.id(), true).orElseThrow();
                if (!token.equals(locked.lease()) || !Set.of("PLANNING", "REVIEW").contains(locked.phase())) return null;
                // A waiting review predates this worker and has not yet taken an active slot.
                if ("REVIEW".equals(locked.phase()) && store.pending(task.tenant(), task.actor()) >= 3) {
                    store.update(locked, token, locked.phase(), locked.engine(), null, 30);
                    return null;
                }
                charges.hold(locked.tenant(), chargeKey(locked), locked.binding(), locked.actor(), charge.units());
                var quote = charges.quote(locked.tenant(), chargeKey(locked));
                if (quote == null || !quote.requested().equals(charge.units())
                        || quote.total().compareTo(charge.quote().points()) != 0)
                    throw input("生成积分已变化，请重新发起复刻");
                store.queueConfirmation(locked, payload.toString());
                return quote.total();
            });
            if (queued != null) LOG.info("Video remake auto-confirmed task {} points {} shots {}", task.id(), queued, plan.path("shots").size());
        } catch (BizException ex) {
            complete(task, token, "FAILED", ex.getMessage(), false, false);
        }
    }

    private void dispatch(Task task, String token) {
        try {
            charges.validate(task.tenant(), chargeKey(task));
        } catch (BizException ex) {
            store.update(task, token, "SENDING", task.engine(), "授权或套餐已变化，确认状态待核对", 60);
            return;
        }
        try {
            var reply = engine.remake("POST", path(task) + "/confirm-plan", parse(task.confirmation()));
            if (!task.engine().equals(reply.path("id").asText()) || !task.tenant().equals(reply.path("store_id").asText()))
                throw input("复刻确认回执待核对");
            store.update(task, token, "GENERATING", task.engine(), null, 8);
        } catch (RemakeEngineError ex) {
            if (ex.status() == 422 || ex.status() == 400)
                complete(task, token, "FAILED", ex.getMessage(), true, false);
            else throw ex;
        }
    }

    private void complete(Task task, String token, String phase, String error, boolean paid, boolean delivered) {
        tx.executeWithoutResult(status -> {
            if (!store.update(task, token, phase, task.engine(), error, 0) || !paid) return;
            if (delivered) charges.finishAll(task.tenant(), chargeKey(task));
            else charges.finish(task.tenant(), chargeKey(task), Map.of());
        });
    }

    private String error(JsonNode result, String fallback) {
        var message = result.path("error").asText("");
        if (message.isBlank()) return fallback;
        if ("SCENE_BUDGET_EXCEEDED".equals(message))
            return "参考视频在此前分析时镜头切换过多，请新建复刻任务重试（SCENE_BUDGET_EXCEEDED）";
        if (message.startsWith("商品事实审核未通过")) {
            var blocking = auditItems(result, "blocking");
            if (!blocking.isEmpty()) message = "商品事实审核未通过：" + String.join("；", blocking);
        }
        return message.substring(0, Math.min(message.length(), 200));
    }

    private void requireGeneration(String tenant, String actor, String binding) {
        if (binding == null || binding.isBlank()) throw input("请选择千川账户");
        charges.requireAccess(tenant);
        bindings.validatePaid(bindings.executionScope(tenant, binding, actor));
    }

    private void requireUpload(String tenant, String actor, String binding, String id) {
        if (!store.ownsUpload(tenant, actor, binding, identifier(id, "rpu_")))
            throw new BizException(404, "未找到当前账户的上传会话");
    }

    private String requireAsset(String tenant, String actor, String binding, String id, String kind) {
        if (!store.ownsAsset(tenant, actor, binding, identifier(id, "rpa_"), kind))
            throw new BizException(404, "未找到当前账户的参考素材");
        return id;
    }

    private Task owned(String tenant, String actor, String id) {
        var task = store.find(tenant, uuid(id), false).filter(row -> actor.equals(row.actor()))
                .orElseThrow(() -> new BizException(404, "未找到复刻任务"));
        access.requireReadableBinding(tenant, task.binding());
        return task;
    }

    private View summary(Task task) {
        return new View(task.id(), task.product(), task.phase(), null, task.error(),
                task.createdAt(), task.asset(), null, null, null, List.of(), null, null, List.of(), List.of(),
                productImageUrls(task), List.of(), List.of(), List.of(), List.of());
    }

    // Keep the stored failure readable when the engine is temporarily unavailable.
    private View failedView(Task task) {
        try {
            var data = detail(task);
            var issues = auditIssues(data);
            var warnings = auditWarnings(data);
            var consistency = consistencyIssues(data);
            var generationWarnings = VideoRemakeGeneration.generationWarnings(data);
            var shotRetries = VideoRemakeGeneration.shotRetries(data);
            if (issues.isEmpty() && warnings.isEmpty() && consistency.isEmpty() && generationWarnings.isEmpty() && shotRetries.isEmpty()) return summary(task);
            return new View(task.id(), task.product(), task.phase(), data.path("stage").asText(null),
                    error(data, task.error()), task.createdAt(), task.asset(), null, null, null, List.of(),
                    null, null, issues, warnings, productImageUrls(task), consistency, generationWarnings, shotRetries,
                    VideoRemakeGeneration.progressSteps(data));
        } catch (RuntimeException ex) {
            return summary(task);
        }
    }
    private List<String> auditIssues(JsonNode data) {
        return auditItems(data, "blocking");
    }
    private List<String> auditWarnings(JsonNode data) {
        return auditItems(data, "warnings");
    }

    private List<String> consistencyIssues(JsonNode data) {
        var failure = data.path("video_consistency_failure");
        if (!failure.isObject()) failure = data.path("result").path("video_consistency_failure");
        if (!failure.isObject()) return List.of();
        var items = new ArrayList<String>();
        String reasonCode = failure.path("reason").asText("");
        int shot = failure.path("shot_index").asInt(0);
        for (var retry : VideoRemakeGeneration.shotRetries(data)) {
            if (shot > 0 && retry.shotIndex() == shot && "pending".equals(retry.status())) {
                items.add("第 " + shot + " 分镜首次抽查未通过，正在重试");
                return List.copyOf(items);
            }
        }
        String detail = switch (reasonCode) {
            case "product_mismatch" -> "商品与参考图不一致";
            case "garbled_text" -> "画面出现无法辨认的乱码或伪文字";
            case "fake_brand" -> "画面出现仿造品牌或商标";
            case "sample_count_mismatch" -> "采样帧数量与抽查批次不一致";
            case "embedded_text" -> "画面出现与商品无关的内嵌文字";
            case "pure_product_people" -> "纯商品镜头出现人物";
            default -> null;
        };
        String first = "生成视频商品一致性检查未通过";
        if (shot > 0 && detail != null) first += "（第 " + shot + " 分镜：" + detail + "）";
        else if (shot > 0) first += "（第 " + shot + " 分镜）";
        else if (detail != null) first += "（" + detail + "）";
        items.add(first);
        var byImage = failure.path("product_matches_by_image");
        if (byImage.isArray() && byImage.size() > 0 && byImage.valueStream().noneMatch(JsonNode::asBoolean)) {
            items.add("无任何一张参考图与画面商品匹配");
        }
        var texts = new ArrayList<String>();
        failure.path("embedded_texts").forEach(entry -> {
            if (entry.isTextual()) {
                String text = entry.asText("");
                if (!text.isBlank()) texts.add(text);
            } else if (entry.isObject()) {
                String text = entry.path("text").asText("");
                if (!text.isBlank()) {
                    String kind = entry.path("kind").asText("");
                    String label = text;
                    if (entry.hasNonNull("frame_index")) label = "采样帧 " + entry.path("frame_index").asInt() + "：" + text;
                    texts.add(kind.isBlank() ? label : label + "（" + kind + "）");
                }
            }
        });
        if (!texts.isEmpty()) items.add("画面内嵌文字：" + String.join("；", texts));
        var frames = new ArrayList<String>();
        failure.path("failed_samples").forEach(sample -> {
            if (sample.hasNonNull("frame_index")) frames.add("采样帧 " + sample.path("frame_index").asInt());
        });
        if (!frames.isEmpty()) items.add("未通过的采样帧：" + String.join("、", frames));
        return List.copyOf(items);
    }

    private List<String> auditItems(JsonNode data, String field) {
        var audit = data.path("product_fact_audit");
        if ((audit.isMissingNode() || audit.isNull()) && data.has("result")) {
            audit = data.path("result").path("product_fact_audit");
        }
        if (!audit.isObject()) return List.of();
        var items = new java.util.LinkedHashSet<String>();
        if (audit.has(field)) {
            audit.path(field).valueStream().filter(JsonNode::isTextual).map(JsonNode::asText).forEach(items::add);
        }
        audit.fields().forEachRemaining(entry -> entry.getValue().path(field).valueStream().filter(JsonNode::isTextual).map(JsonNode::asText).forEach(items::add));
        return List.copyOf(items);
    }

    private List<String> productImageUrls(Task task) {
        String request = task.request();
        if (request == null || request.isBlank()) return List.of();
        var urls = parse(request).path("product").path("image_urls");
        if (!urls.isArray()) return List.of();
        return urls.valueStream().filter(JsonNode::isTextual).map(JsonNode::asText).map(String::strip)
                .filter(VideoRemakeService::isProductImageUrl).distinct().limit(6).toList();
    }

    private JsonNode detail(Task task) {
        var result = engine.remake("GET", path(task) + scope(task.tenant()), null);
        if (!task.tenant().equals(result.path("store_id").asText()) || !task.engine().equals(result.path("id").asText()))
            throw input("复刻任务归属不匹配");
        return result;
    }

    private String path(Task task) { return "/tasks/" + identifier(task.engine(), "rpt_"); }
    private String scope(String tenant) { return "?store_id=" + CreationEngineClient.query(tenant); }
    private String chargeKey(Task task) { return "remake:" + task.id(); }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Invalid stored remake request", ex); }
    }

    private static boolean isProductImageUrl(String url) {
        if (url.length() > 4096) return false;
        try {
            URI parsed = URI.create(url);
            return "https".equalsIgnoreCase(parsed.getScheme()) && parsed.getHost() != null
                    && parsed.getUserInfo() == null && parsed.getFragment() == null
                    && (parsed.getPort() == -1 || parsed.getPort() == 443);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private String uuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw input("任务标识无效");
        return value;
    }

    private String identifier(String value, String prefix) {
        if (value == null || !value.matches(prefix + "[a-f0-9]{32}")) throw input("复刻素材或任务标识无效");
        return value;
    }

    private String text(String value, int max) { return text(value, 1, max); }
    private String text(String value, int min, int max) {
        String normalized = value == null ? "" : value.strip();
        int size = normalized.codePointCount(0, normalized.length());
        if (size < min || size > max) throw input("请核对商品信息、人物名称或分镜的必填内容和字数");
        return normalized;
    }

    private BizException input(String message) { return new BizException(1402, message); }
}
