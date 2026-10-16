package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Read-only fallback: platform receipts identify the same asset, never its filename. */
@Service
public class AssetPreviewService {
    private final AssetUploadRepository assets;
    private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;
    private final MaterialLibraryPort library;
    private com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog platformCatalog;
    @org.springframework.beans.factory.annotation.Autowired
    public void platformCatalog(com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog catalog) {
        this.platformCatalog = catalog;
    }
    private final ConcurrentHashMap<Key, Cached> cache = new ConcurrentHashMap<>();
    private record Key(String tenant, String account, String advertiser, MaterialType type, String object, String material) {}
    private record Cached(List<MaterialLibraryPort.PlatformMaterial> items, Instant expires) {}
    private final ConcurrentHashMap<Key, Cached> platformCache = new ConcurrentHashMap<>();
    public record Preview(String posterUrl, String playUrl, String source, String message) {}

    public AssetPreviewService(AssetUploadRepository assets, OauthAccountRepository accounts,
            QianchuanCredentialFacade credentials, MaterialLibraryPort library) {
        this.assets = assets; this.accounts = accounts; this.credentials = credentials; this.library = library;
    }

    public Preview preview(TenantId tenant, String id) {
        var asset = assets.require(tenant.value(), id);
        if (asset.canonicalId() != null) asset = assets.require(tenant.value(), asset.canonicalId());
        if (!List.of("VIDEO", "IMAGE").contains(asset.kind())) return unavailable("此素材类型暂无千川预览副本");
        var type = MaterialType.valueOf(asset.kind());
        boolean uploaded = false;
        for (var sync : assets.syncs(tenant.value(), asset.id())) {
            if (!"UPLOADED".equals(sync.status()) || !present(sync.objectId()) && !present(sync.materialId())) continue;
            uploaded = true;
            try {
                var accountId = new AccountId(sync.accountId());
                var account = accounts.findById(tenant, accountId).orElse(null);
                if (account == null || account.platform() != Platform.QIANCHUAN || !"ACTIVE".equals(account.status())) continue;
                var credential = credentials.getUsableCredential(tenant, accountId);
                if (!credential.advertiserId().value().equals(sync.advertiserId())) continue;
                var key = new Key(tenant.value(), sync.accountId(), sync.advertiserId(), type, sync.objectId(), sync.materialId());
                // Each request still checks current ownership, sync status and authorization before cache access.
                var now = Instant.now();
                cache.entrySet().removeIf(e -> !e.getValue().expires().isAfter(now));
                if (cache.size() > 256) cache.clear();
                var cached = cache.compute(key, (k, old) -> old != null && old.expires().isAfter(now) ? old :
                        new Cached(library.list(new MaterialLibraryPort.MaterialLibraryQuery(
                            k.advertiser(), type, null, null, 1, 20, credential.accessToken(), k.object(), k.material())).items(), now.plusSeconds(30)));
                for (var material : cached.items()) {
                    if (!(present(sync.objectId()) && sync.objectId().equals(material.objectId())
                            || present(sync.materialId()) && sync.materialId().equals(material.materialId()))) continue;
                    String play = safeUrl(material.url()), poster = safeUrl(material.posterUrl());
                    if (type == MaterialType.IMAGE) poster = present(play) ? play : poster;
                    if (present(poster) || present(play)) return new Preview(poster, play, "QIANCHUAN", "");
                }
            } catch (RuntimeException unavailable) {
                // Do not expose provider payloads, signed URLs or credentials in a thumbnail error.
            }
        }
        return unavailable(uploaded
                ? "原文件暂不可读取，千川副本也暂无可用预览地址；请检查存储服务及千川素材预览权限"
                : "原文件暂不可读取，且没有已同步的千川副本；请恢复存储服务后刷新");
    }

    /** Resolve a catalog entry through its owned account and stable provider IDs. */
    public Preview platformPreview(TenantId tenant, String id, boolean refresh) {
        if (platformCatalog == null || id == null || id.length() > 320) return unavailable("素材预览不可用");
        String[] parts = id.split(":", 4);
        if (parts.length != 4 || !"EXT".equals(parts[0])) return unavailable("素材预览不可用");
        MaterialType type;
        try { type = MaterialType.valueOf(parts[2]); }
        catch (IllegalArgumentException invalid) { return unavailable("素材预览不可用"); }
        var material = platformCatalog.find(tenant.value(), parts[1], type, parts[3]).orElse(null);
        if (material == null) return unavailable("素材已移除或当前账号无权访问");
        try {
            var accountId = new AccountId(material.accountId());
            var account = accounts.findById(tenant, accountId).orElse(null);
            if (account == null || account.platform() != Platform.QIANCHUAN || !"ACTIVE".equals(account.status()))
                return unavailable("千川账号授权已失效");
            var credential = credentials.getUsableCredential(tenant, accountId);
            if (!credential.advertiserId().value().equals(material.advertiserId()))
                return unavailable("千川账号已变更，请重新同步素材");
            var key = new Key(tenant.value(), material.accountId(), material.advertiserId(), type,
                    material.objectId(), material.materialId());
            var now = Instant.now();
            if (platformCache.size() > 512) platformCache.clear();
            var cached = platformCache.compute(key, (k, old) -> !refresh && old != null && old.expires().isAfter(now)
                    ? old : new Cached(fetchPlatformMaterial(k, credential.accessToken()),
                            now.plusSeconds(type == MaterialType.VIDEO ? 2700 : 300)));
            for (var entry : cached.items()) {
                if (!material.objectId().equals(entry.objectId())) continue;
                if (present(material.materialId()) && !material.materialId().equals(entry.materialId())) continue;
                String play = safeUrl(entry.url()), poster = safeUrl(entry.posterUrl());
                if (type == MaterialType.IMAGE) poster = present(play) ? play : poster;
                if (present(poster) || present(play)) return new Preview(poster, play, "QIANCHUAN", "");
            }
        } catch (RuntimeException unavailable) {
            // Never return upstream payloads, tokens or expired signed links in errors.
        }
        return unavailable("千川预览链接暂不可用；请检查素材预览权限或稍后重试");
    }

    private List<MaterialLibraryPort.PlatformMaterial> fetchPlatformMaterial(Key key, String token) {
        if (key.type() != MaterialType.CAROUSEL) {
            return library.list(new MaterialLibraryPort.MaterialLibraryQuery(key.advertiser(), key.type(),
                    null, null, 1, 20, token, key.object(), key.material())).items();
        }
        // The carousel endpoint has no verified ID filter. Scan bounded pages and match the stable ID.
        for (int page = 1; page <= 50; page++) {
            var items = library.list(new MaterialLibraryPort.MaterialLibraryQuery(key.advertiser(), key.type(),
                    null, null, page, 20, token)).items();
            var match = items.stream().filter(m -> key.object().equals(m.objectId())).findFirst();
            if (match.isPresent()) return List.of(match.get());
            if (items.size() < 20) break;
        }
        return List.of();
    }

    private static Preview unavailable(String message) { return new Preview("", "", "UNAVAILABLE", message); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String safeUrl(String value) {
        if (!present(value)) return "";
        try { var uri = URI.create(value); return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null ? value : ""; }
        catch (IllegalArgumentException invalid) { return ""; }
    }
}
