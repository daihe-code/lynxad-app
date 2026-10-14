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
    private final ConcurrentHashMap<Key, Cached> cache = new ConcurrentHashMap<>();
    private record Key(String tenant, String account, String advertiser, MaterialType type, String object, String material) {}
    private record Cached(List<MaterialLibraryPort.PlatformMaterial> items, Instant expires) {}
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

    private static Preview unavailable(String message) { return new Preview("", "", "UNAVAILABLE", message); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String safeUrl(String value) {
        if (!present(value)) return "";
        try { var uri = URI.create(value); return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null ? value : ""; }
        catch (IllegalArgumentException invalid) { return ""; }
    }
}
