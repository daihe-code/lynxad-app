package com.lynx.ad.modules.creative.application;

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
import com.lynx.ad.modules.creative.infrastructure.persistence.VideoRemakeStore;
import com.lynx.ad.modules.creative.infrastructure.persistence.VideoRemakeStore.Task;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.identity.application.AccountBindingRegistry;
import com.lynx.ad.modules.tenant.application.WorkspaceAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VideoRemakeService {
    private static final Logger LOG = LoggerFactory.getLogger(VideoRemakeService.class);
    private static final Map<String, Integer> ONE_VIDEO = Map.of("video", 1);
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

    public record Capability(boolean ready, boolean subscribed, String balanceStatus, BigDecimal points) {}
    public record Upload(String requestId, String mediaType, String filename, long size, String sha256) {}
    public record Start(String requestId, String bindingId, String awemeId, String productId, List<String> facts,
                        String productImageId, String referenceId, String portraitId, String portraitName,
                        boolean rightsConfirmed, boolean portraitTermsConfirmed, int durationSeconds) {}
    public record ShotEdit(String visualPrompt, String narration, String caption) {}
    public record Confirm(String title, List<ShotEdit> shots, boolean accepted, BigDecimal points) {}
    public record View(String id, String product, String phase, String stage, String error, Instant createdAt,
                       String assetId, JsonNode plan, String videoUrl, String coverUrl) {}

    public Capability capability(String tenant) {
        var membership = charges.access(tenant);
        var rate = pricing.rate("video");
        if (!membership.enabled()) return new Capability(false, false, null, rate);
        try {
            var status = engine.remake("GET", "/capabilities", null);
            return new Capability(status.path("ready").asBoolean() && status.path("review_worker_ready").asBoolean(),
                    true, membership.balanceStatus().name(), rate);
        } catch (RemakeEngineError ex) {
            return new Capability(false, true, membership.balanceStatus().name(), rate);
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

    public View create(String tenant, String actor, Start request) {
        String id = uuid(request.requestId());
        var existing = store.find(tenant, id, false);
        if (existing.isPresent()) return summary(owned(tenant, actor, id));
        requireGeneration(tenant, actor, request.bindingId());
        if (!request.rightsConfirmed() || !request.portraitTermsConfirmed()) throw input("请确认参考视频和预置人像的使用授权");
        if (!Set.of(15, 30).contains(request.durationSeconds())) throw input("请选择 15 秒或 30 秒");
        if (request.facts() == null || request.facts().isEmpty() || request.facts().size() > 12) throw input("请填写 1 至 12 条真实商品事实");
        var facts = request.facts().stream().map(value -> text(value, 200)).toList();
        String productId = text(request.productId(), 19);
        var product = catalog.restorable(new TenantId(tenant), request.bindingId(), text(request.awemeId(), 128), List.of(productId))
                .stream().filter(row -> productId.equals(row.qcProductId())).findFirst()
                .orElseThrow(() -> input("所选商品已不可用，请重新选择"));
        String portrait = text(request.portraitId(), 136).replaceFirst("^asset://", "");
        if (!portrait.matches("asset-[A-Za-z0-9_-]{4,120}")) throw input("请填写有效的方舟预置人像 ID");
        String productName = text(product.name(), 200);
        var target = json.createObjectNode().put("id", productId).put("name", productName);
        target.set("facts", json.valueToTree(facts));
        if (request.productImageId() != null && !request.productImageId().isBlank()) {
            target.putArray("image_asset_ids").add(requireAsset(tenant, actor, request.bindingId(), request.productImageId(), "image"));
        } else {
            if (product.imageUrl() == null || !product.imageUrl().startsWith("https://")) throw input("请上传目标商品参考图");
            target.putArray("image_urls").add(product.imageUrl());
        }
        var payload = json.createObjectNode().put("store_id", tenant).put("request_id", id)
                .put("source_material_id", requireAsset(tenant, actor, request.bindingId(), request.referenceId(), "video"))
                .put("orientation", "portrait").put("duration_seconds", request.durationSeconds())
                .put("max_shots", 4).put("rights_confirmed", true).put("costs_confirmed", true)
                .put("require_plan_confirmation", true);
        payload.set("product", target);
        payload.putObject("portrait").put("source", "ark_preset").put("asset_id", portrait)
                .put("label", text(request.portraitName(), 80))
                .put("platform_terms_confirmed", true).put("adult_confirmed", true);
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
        if (task.engine() == null || "FAILED".equals(task.phase())) return summary(task);
        if (!Set.of("REVIEW", "SUCCEEDED", "GENERATING", "PLANNING").contains(task.phase())) return summary(task);
        var data = detail(task);
        var result = data.path("delivery");
        boolean finished = "SUCCEEDED".equals(task.phase());
        return new View(task.id(), task.product(), task.phase(), data.path("stage").asText(null), task.error(),
                task.createdAt(), task.asset(), data.get("plan"), finished ? result.path("video_url").asText(null) : null,
                finished ? result.path("cover_url").asText(null) : null);
    }

    public View confirm(String tenant, String actor, String id, Confirm request) {
        var task = owned(tenant, actor, id);
        if (!"REVIEW".equals(task.phase())) return summary(task);
        if (!request.accepted() || request.points() == null || pricing.rate("video").compareTo(request.points()) != 0)
            throw input("请重新核对分镜及本次生成所需积分");
        var plan = detail(task).path("plan");
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
            charges.hold(tenant, chargeKey(locked), locked.binding(), actor, ONE_VIDEO);
            var quote = charges.quote(tenant, chargeKey(locked));
            if (quote == null || quote.rates().get("video").compareTo(request.points()) != 0)
                throw input("生成积分已变化，请刷新后再确认");
            store.queueConfirmation(locked, payload.toString());
            return summary(store.find(tenant, id, false).orElseThrow());
        });
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
            var reply = engine.remake("POST", "/tasks", parse(task.request()));
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
        boolean paid = !"PLANNING".equals(task.phase());
        switch (remote) {
            case "awaiting_confirmation" -> {
                if (paid) throw input("引擎确认记录待核对");
                store.update(task, token, "REVIEW", task.engine(), null, 0);
            }
            case "succeeded" -> {
                if (!paid || !current.path("delivery").path("typography_passed").asBoolean()
                        || current.path("delivery").path("video_url").asText().isBlank()
                        || current.path("delivery").path("cover_url").asText().isBlank())
                    throw input("成片尚未通过交付核对");
                complete(task, token, "SUCCEEDED", null, true, true);
            }
            case "failed" -> complete(task, token, "FAILED", error(current, "复刻任务执行失败"), paid, false);
            case "needs_attention" -> store.update(task, token, "NEEDS_ATTENTION", task.engine(),
                    error(current, "生成结果待核对，积分仍在预占中"), 60);
            case "review_queued", "review_processing" -> store.update(task, token,
                    paid ? "GENERATING" : "PLANNING", task.engine(), error(current, null), 8);
            default -> throw input("复刻任务状态待核对");
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
            if (store.update(task, token, phase, task.engine(), error, 0) && paid)
                charges.finish(task.tenant(), chargeKey(task), delivered ? ONE_VIDEO : Map.of());
        });
    }

    private String error(JsonNode result, String fallback) {
        var message = result.path("error").asText("");
        return message.isBlank() ? fallback : message.substring(0, Math.min(message.length(), 200));
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
                task.createdAt(), task.asset(), null, null, null);
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
