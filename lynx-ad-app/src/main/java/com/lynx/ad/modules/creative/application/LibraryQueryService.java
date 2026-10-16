package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.creative.api.dto.LibraryItemVo;
import com.lynx.ad.modules.creative.api.dto.LibraryResponse;
import com.lynx.ad.modules.creative.api.dto.LibraryVideoVo;
import com.lynx.ad.modules.creative.domain.model.CreativeArtifact;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.model.MaterialSource;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class LibraryQueryService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private CarouselService carousels;
    @org.springframework.beans.factory.annotation.Autowired
    public void carouselDependencies(CarouselService service) { this.carousels=service; }

    private final CreativeTaskRepository repository;
    private final StoragePort storagePort;
    private final MaterialLibraryPort materialLibraryPort;
    private final OauthQueryService oauthQueryService;
    private final PlanCreativeBindingQuery bindingQuery;
    private final ProductQueryService productQueryService;
    private com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository uploads;
    private com.lynx.ad.modules.identity.domain.port.OauthAccountRepository accounts;
    private com.lynx.ad.modules.identity.application.QianchuanCredentialFacade credentials;
    private com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog platformCatalog;
    @org.springframework.beans.factory.annotation.Autowired
    public void catalogDependency(com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog catalog) {
        this.platformCatalog = catalog;
    }
    @org.springframework.beans.factory.annotation.Autowired
    public void uploadDependencies(com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository uploads,
            com.lynx.ad.modules.identity.domain.port.OauthAccountRepository accounts,
            com.lynx.ad.modules.identity.application.QianchuanCredentialFacade credentials) {
        this.uploads=uploads; this.accounts=accounts; this.credentials=credentials;
    }

    public LibraryQueryService(
            CreativeTaskRepository repository,
            StoragePort storagePort,
            MaterialLibraryPort materialLibraryPort,
            OauthQueryService oauthQueryService,
            PlanCreativeBindingQuery bindingQuery,
            ProductQueryService productQueryService) {
        this.repository = repository;
        this.storagePort = storagePort;
        this.materialLibraryPort = materialLibraryPort;
        this.oauthQueryService = oauthQueryService;
        this.bindingQuery = bindingQuery;
        this.productQueryService = productQueryService;
    }

    public LibraryResponse query(
            TenantId tenantId,
            String typeRaw,
            String platform,
            LocalDate from,
            LocalDate to,
            String status,
            String q,
            int page,
            int pageSize) {
        return query(tenantId,typeRaw,platform,from,to,status,q,page,pageSize,false);
    }

    public LibraryResponse query(TenantId tenantId,String typeRaw,String platform,LocalDate from,LocalDate to,
            String status,String q,int page,int pageSize,boolean selectable) {
        String type = parseType(typeRaw);
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? (selectable ? LocalDate.of(1970,1,1) : today.minusDays(6)) : from;
        if (start.isAfter(end)) {
            throw new BizException(1509, "开始时间不能晚于结束时间");
        }
        int size = pageSize <= 0 ? 8 : Math.min(pageSize, 20);
        int pg = Math.max(page, 1);
        String st = status == null || status.isBlank() ? "ALL" : status.toUpperCase(Locale.ROOT);
        if (platform != null && !platform.isBlank() && !"QIANCHUAN".equalsIgnoreCase(platform)) {
            return empty(false, pg, size);
        }
        boolean bound = oauthQueryService.hasBoundQianchuan(tenantId);
        List<Item> merged = new ArrayList<>(localItems(tenantId));
        merged.addAll(uploadedItems(tenantId));
        merged.addAll(carouselItems(tenantId));
        String platformWarning=null;
        // The creation picker accepts durable platform asset IDs, not standalone remote library IDs.
        // Avoid fetching unselectable remote entries that would otherwise occupy the first page.
        if (bound && !selectable) {
            try {
                merged.addAll(platformCatalog == null ? platformItems(tenantId, start, end) : catalogItems(tenantId, start, end));
                if (platformCatalog != null && platformCatalog.hasFailedSync(tenantId.value()))
                    platformWarning = "千川素材同步暂时失败，当前展示上次成功同步的目录；请检查授权或稍后重试";
                else if (platformCatalog != null && !platformCatalog.hasCompletedSync(tenantId.value()))
                    platformWarning = "千川素材目录正在后台同步，已同步素材会逐步显示";
            } catch (BizException ex) {
                if (merged.isEmpty()) throw ex;
                platformWarning="千川素材暂时无法查询，当前展示已保存到本平台的素材";
            } catch (Exception ex) {
                if (merged.isEmpty()) throw new BizException(1506, "素材库查询失败");
                platformWarning="千川素材暂时无法查询，当前展示已保存到本平台的素材";
            }
        }
        merged = dedup(merged);
        if (selectable) merged=merged.stream().filter(LibraryQueryService::selectable).toList();
        Set<String> usedKeys = bindingQuery.usedKeys(tenantId);
        for (Item item : merged) {
            item.used = used(item, usedKeys);
        }
        LibraryResponse.TabCounts tabs = tabs(merged, start, end);
        List<Item> typed = merged.stream()
                .filter(i -> i.type.equals(type))
                .filter(i -> inRange(i.createdAt, start, end))
                .toList();
        List<Item> searched = typed.stream().filter(i -> matchQ(i, q)).toList();
        int usedCount = (int) searched.stream().filter(i -> "USED".equals(statusOf(i))).count();
        int unusedCount = (int) searched.stream().filter(i -> "UNUSED".equals(statusOf(i))).count();
        int reviewingCount = (int) searched.stream().filter(i -> "REVIEWING".equals(statusOf(i))).count();
        int rejectedCount = (int) searched.stream().filter(i -> "REJECTED".equals(statusOf(i))).count();
        LibraryResponse.StatusCounts statuses =
                new LibraryResponse.StatusCounts(searched.size(), usedCount, unusedCount, reviewingCount, rejectedCount);
        List<Item> filtered = searched.stream()
                .filter(i -> "ALL".equals(st) || st.equals(statusOf(i)))
                .sorted(Comparator.comparing((Item i) -> i.createdAt).reversed().thenComparing(i -> i.objectId, Comparator.reverseOrder()))
                .toList();
        int fromIdx = Math.min((pg - 1) * size, filtered.size());
        int toIdx = Math.min(fromIdx + size, filtered.size());
        List<LibraryItemVo> pageItems = filtered.subList(fromIdx, toIdx).stream().map(i -> toVo(tenantId, i)).toList();
        return new LibraryResponse(
                bound, tabs, statuses, new LibraryResponse.PageInfo(pg, size, filtered.size()), pageItems, platformWarning);
    }

    private static boolean selectable(Item item) {
        if (item.carouselId!=null) return item.aiReview!=null && Set.of("APPROVED","NOT_REQUIRED").contains(item.aiReview.status());
        return (item.id.startsWith("UPLOAD:") || item.id.startsWith("ENGINE:")) && !"REJECTED".equals(item.reviewBadge);
    }

    public List<LibraryVideoVo> libraryVideos(TenantId tenantId) {
        List<Item> merged = new ArrayList<>(localItems(tenantId));
        if (oauthQueryService.hasBoundQianchuan(tenantId)) {
            try {
                merged.addAll(platformCatalog == null
                        ? platformItems(tenantId, LocalDate.of(1970, 1, 1), LocalDate.of(2099, 12, 31))
                        : catalogItems(tenantId, null, null));
            } catch (BizException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BizException(1506, "素材库查询失败");
            }
        }
        return dedup(merged).stream()
                .filter(i -> i.type.equals("VIDEO"))
                .map(i -> new LibraryVideoVo(
                        i.objectId == null ? "" : i.objectId,
                        productIdOf(i),
                        i.coverImageId))
                .toList();
    }

    private List<Item> localItems(TenantId tenantId) {
        Map<String, CreativePackage> latest = new HashMap<>();
        for (CreativePackage pkg : repository.listPackagesByTenant(tenantId)) {
            for (CreativeArtifact art : pkg.items()) {
                if (art.mockContent()) continue;
                String pid = art.productId().value();
                CreativePackage prev = latest.get(pid);
                if (prev == null || pkg.version() >= prev.version()) {
                    latest.put(pid, pkg);
                }
            }
        }
        List<Item> items = new ArrayList<>();
        for (var e : latest.entrySet()) {
            CreativePackage pkg = e.getValue();
            for (CreativeArtifact art : pkg.items()) {
                if (art.mockContent() || !art.productId().value().equals(e.getKey())) {
                    continue;
                }
                Instant created = art.createdAt() != null ? art.createdAt() : pkg.createdAt();
                items.add(new Item(
                        "LYNX:" + pkg.id().value() + ":" + art.productId().value(),
                        MaterialType.VIDEO,
                        MaterialSource.LYNX,
                        art.filename() == null ? art.productId().value() + ".mp4" : art.filename(),
                        art.sizeBytes() == null ? 0 : art.sizeBytes(),
                        art.imageMode() == null ? "VIDEO_VERTICAL" : art.imageMode(),
                        art.durationSec(),
                        productQueryService.nameOf(art.productId().value()),
                        created,
                        false,
                        "NONE",
                        art.videoId().map(v -> v.value()).orElse(null),
                        art.videoId().map(v -> v.value()).orElse(pkg.id().value()),
                        art.coverImageId().map(c -> c.value()).orElse(null),
                        "",
                        "",
                        null,
                        null));
                var item = items.getLast();
                item.storageObjectKey = art.videoObjectKey();
                item.storageCoverKey = art.coverObjectKey() == null ? art.videoObjectKey() : art.coverObjectKey();
            }
        }
        return items;
    }

    private List<Item> uploadedItems(TenantId tenant) {
        if (uploads==null) return List.of();
        List<Item> result=new ArrayList<>();
        var assets = uploads.list(tenant.value());
        var details = uploads.libraryDetails(tenant.value(), assets);
        for(var asset:assets) {
            boolean generated="engine_oss".equals(asset.storageMode());
            var meta = details == null ? null : details.metadata().get(asset.id());
            var legacyMeta = details == null ? uploads.storageMetadata(tenant.value(),asset.id()) : null;
            String coverKey = meta == null ? null : meta.previewKey();
            String coverMode = meta == null ? null : meta.previewMode();
            if (legacyMeta != null && legacyMeta.previewAssetId() != null) {
                var cover = uploads.require(tenant.value(), legacyMeta.previewAssetId());
                coverKey = cover.objectKey(); coverMode = cover.storageMode();
            }
            Long durationMs = meta != null ? meta.durationMs() : legacyMeta != null ? legacyMeta.durationMs() : null;
            var item=new Item((generated?"ENGINE:":"UPLOAD:")+asset.id(),asset.kind(),generated?MaterialSource.LYNX:MaterialSource.UPLOAD,asset.filename(),asset.sizeBytes(),
                    asset.width()!=null && asset.height()!=null ? (asset.width()>asset.height() ? "HORIZONTAL" : asset.width().equals(asset.height()) ? "SQUARE" : "VERTICAL") : "",
                    durationMs == null ? null : Math.toIntExact(durationMs / 1000),
                    generated?"AI 智能生成":"本地上传",asset.createdAt(),false,"NONE",null,asset.id(),null,
                    "", "",asset.width(),asset.height());
            if (!"TEXT".equals(asset.kind())) {
                item.storageObjectKey = asset.objectKey(); item.storageObjectMode = asset.storageMode();
                item.storageCoverKey = "IMAGE".equals(asset.kind()) ? asset.objectKey() : coverKey;
                item.storageCoverMode = "IMAGE".equals(asset.kind()) ? asset.storageMode() : coverMode;
            }
            item.syncs = details == null ? uploads.syncs(tenant.value(),asset.id())
                    : details.syncs().getOrDefault(asset.id(), List.of());
            item.textContent=asset.textContent(); result.add(item);
            item.aiReview = details == null ? uploads.aiReview(tenant.value(),asset.id()) : details.reviews().get(asset.id());
            item.reviewBadge=effectiveBadge(item);
        }
        return result;
    }

    private List<Item> platformItems(TenantId tenant, LocalDate start, LocalDate end) {
        List<Item> items = new ArrayList<>();
        if (accounts!=null) {
            for(var account:accounts.findByTenant(tenant)) {
                if (!"ACTIVE".equals(account.status()) || account.platform()!=com.lynx.ad.modules.identity.domain.model.Platform.QIANCHUAN) continue;
                var credential=credentials.getUsableCredential(tenant,account.id());
                items.addAll(platformAccountItems(account.id().value(),credential.advertiserId().value(),credential.accessToken(),start,end));
            }
            return items;
        }
        return platformAccountItems("", "bound", null, start, end);
    }
    private List<Item> catalogItems(TenantId tenant, LocalDate start, LocalDate end) {
        List<Item> result = new ArrayList<>();
        var rows = start == null || end == null ? platformCatalog.list(tenant.value())
                : platformCatalog.list(tenant.value(), start.atStartOfDay(SHANGHAI).toInstant(),
                        end.plusDays(1).atStartOfDay(SHANGHAI).toInstant());
        for (var m : rows) {
            var item = new Item("EXT:" + m.accountId() + ":" + m.type().name() + ":" + m.entryKey(),
                    m.type(), MaterialSource.EXTERNAL, m.filename(), m.sizeBytes(), m.imageMode(),
                    m.durationSec(), "未关联", m.createdAt(), false,
                    m.reviewBadge() == null ? "NONE" : m.reviewBadge(), m.materialId(), m.objectId(), null,
                    "", "", m.width(), m.height());
            item.accountId = m.accountId();
            result.add(item);
        }
        return result;
    }
    private List<Item> carouselItems(TenantId tenant) {
        if(carousels==null) return List.of();
        List<Item> result=new ArrayList<>();
        for(var group:carousels.list(tenant)) {
            var images=group.parts().stream().filter(p->"IMAGE".equals(p.role())).toList();
            var image=images.getFirst().asset();
            long size=group.parts().stream().mapToLong(p->p.asset().sizeBytes()).sum();
            var item=new Item("CAROUSEL:"+group.id(),"CAROUSEL","NOT_REQUIRED".equals(group.aiReview().status())?MaterialSource.LYNX:MaterialSource.UPLOAD,group.name(),size,"",null,
                    "图文 · "+images.size()+" 张图片",Instant.parse(group.createdAt()),false,"NONE",null,group.id(),null,
                    image.url(),image.url(),image.width(),image.height());
            item.carouselId=group.id(); item.aiReview=group.aiReview();
            item.textContent=group.parts().stream().filter(p->"TEXT".equals(p.role())).findFirst().orElseThrow().asset().textContent();
            item.syncs=group.parts().stream().flatMap(p->p.asset().syncs().stream()).filter(s->group.accountIds().contains(s.accountId())).toList();
            item.reviewBadge=effectiveBadge(item); result.add(item);
        }
        return result;
    }
    private List<Item> platformAccountItems(String accountId, String advertiserId, String token, LocalDate start, LocalDate end) {
        List<Item> items = new ArrayList<>();
        for (MaterialType type : MaterialType.values()) {
            var page = materialLibraryPort.list(new MaterialLibraryPort.MaterialLibraryQuery(
                    advertiserId, type, start, end, 1, 20, token));
            for (var m : page.items()) {
                items.add(new Item(
                        "EXT:" + accountId + ":" + type.name() + ":" + m.objectId(),
                        type,
                        MaterialSource.EXTERNAL,
                        m.filename(),
                        m.sizeBytes(),
                        m.imageMode(),
                        m.durationSec(),
                        "未关联",
                        m.createdAt(),
                        false,
                        m.reviewBadge() == null ? "NONE" : m.reviewBadge(),
                        m.materialId(),
                        m.objectId(),
                        null,
                        m.posterUrl(),
                        m.url(),
                        m.width(),
                        m.height()));
                items.getLast().accountId=accountId;
            }
        }
        return items;
    }

    private List<Item> dedup(List<Item> items) {
        Map<String, Item> byId = new HashMap<>();
        Set<String> uploadedKeys=new java.util.HashSet<>();
        Map<String,Item> uploadedByKey=new HashMap<>();
        for(var item:items) for(var sync:item.syncs) if ("UPLOADED".equals(sync.status())) {
            if(sync.materialId()!=null) uploadedKeys.add(sync.accountId()+":"+item.type+":"+sync.materialId());
            if(sync.objectId()!=null) uploadedKeys.add(sync.accountId()+":"+item.type+":"+sync.objectId());
            if(sync.materialId()!=null) uploadedByKey.put(sync.accountId()+":"+item.type+":"+sync.materialId(),item);
            if(sync.objectId()!=null) uploadedByKey.put(sync.accountId()+":"+item.type+":"+sync.objectId(),item);
        }
        for (Item item : items) {
            if(item.source==MaterialSource.EXTERNAL && (uploadedKeys.contains(item.accountId+":"+item.type+":"+item.materialId) || uploadedKeys.contains(item.accountId+":"+item.type+":"+item.objectId))) {
                Item local=uploadedByKey.get(item.accountId+":"+item.type+":"+item.materialId);
                if(local==null) local=uploadedByKey.get(item.accountId+":"+item.type+":"+item.objectId);
                if(local!=null && Set.of("REVIEWING","REJECTED","PASS").contains(item.reviewBadge)) {
                    var owner=local;
                    owner.syncs=owner.syncs.stream().map(s -> {
                        if(!s.accountId().equals(item.accountId) || !(s.materialId()!=null && s.materialId().equals(item.materialId) || s.objectId()!=null && s.objectId().equals(item.objectId))) return s;
                        if(uploads!=null) uploads.review(s,item.reviewBadge);
                        return new com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync(s.id(),s.tenantId(),s.assetId(),s.accountId(),s.advertiserId(),s.status(),s.objectId(),s.materialId(),s.requestId(),item.reviewBadge,s.attempts(),s.nextAttemptAt(),s.leaseUntil(),s.errorMessage());
                    }).toList();
                    owner.reviewBadge=effectiveBadge(owner);
                }
                continue;
            }
            String key = item.source==MaterialSource.UPLOAD ? item.id : item.accountId+":"+item.type+":"+(item.materialId != null ? item.materialId : item.objectId);
            Item exist = byId.get(key);
            if (exist == null || exist.source == MaterialSource.EXTERNAL && item.source == MaterialSource.LYNX) {
                byId.put(key, item);
            }
        }
        return new ArrayList<>(byId.values());
    }
    private static String reviewBadge(List<com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync> syncs) {
        if(syncs.stream().anyMatch(s -> "REJECTED".equals(s.reviewStatus()))) return "REJECTED";
        if(syncs.stream().anyMatch(s -> "REVIEWING".equals(s.reviewStatus()))) return "REVIEWING";
        return "NONE";
    }
    private static String effectiveBadge(Item item) {
        if(item.aiReview!=null) {
            if("REJECTED".equals(item.aiReview.status()) || item.carouselId!=null && "FAILED".equals(item.aiReview.status())) return "REJECTED";
            if(Set.of("QUEUED","RUNNING","PENDING","MANUAL_REVIEW").contains(item.aiReview.status())) return "REVIEWING";
        }
        return reviewBadge(item.syncs);
    }

    private LibraryResponse.TabCounts tabs(List<Item> merged, LocalDate start, LocalDate end) {
        int v = 0, i = 0, c = 0, a = 0, t = 0;
        for (Item item : merged) {
            if (!inRange(item.createdAt, start, end)) {
                continue;
            }
            switch (item.type) {
                case "VIDEO" -> v++;
                case "IMAGE" -> i++;
                case "CAROUSEL" -> c++;
                case "AUDIO" -> a++;
                case "TEXT" -> t++;
            }
        }
        return new LibraryResponse.TabCounts(v, i, c, a, t);
    }

    private static boolean used(Item item, Set<String> keys) {
        if(item.syncs.stream().anyMatch(s -> s.objectId()!=null && keys.contains(s.objectId()) || s.materialId()!=null && keys.contains(s.materialId()))) return true;
        return (item.objectId != null && keys.contains(item.objectId))
                || (item.materialId != null && keys.contains(item.materialId));
    }

    private static String statusOf(Item item) {
        if ("REVIEWING".equals(item.reviewBadge) || "REJECTED".equals(item.reviewBadge)) {
            return item.reviewBadge;
        }
        return item.used ? "USED" : "UNUSED";
    }

    private static boolean inRange(Instant createdAt, LocalDate start, LocalDate end) {
        if (createdAt == null) {
            return true;
        }
        LocalDate d = createdAt.atZone(SHANGHAI).toLocalDate();
        return !d.isBefore(start) && !d.isAfter(end);
    }

    private static boolean matchQ(Item item, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.toLowerCase(Locale.ROOT);
        return contains(item.filename, needle) || contains(item.materialId, needle) || contains(item.objectId, needle) || contains(item.textContent,needle)
                || item.syncs.stream().anyMatch(s -> contains(s.materialId(),needle) || contains(s.objectId(),needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private LibraryItemVo toVo(TenantId tenant, Item item) {
        String play = item.storageObjectKey == null ? item.playUrl :
                com.lynx.ad.modules.creative.domain.port.RoutedStoragePort.select(storagePort,item.storageObjectMode)
                        .getUrl(tenant,item.storageObjectKey);
        String poster = item.storageCoverKey == null ? item.posterUrl
                : item.storageCoverKey.equals(item.storageObjectKey)
                        && java.util.Objects.equals(item.storageCoverMode,item.storageObjectMode) ? play
                : com.lynx.ad.modules.creative.domain.port.RoutedStoragePort.select(storagePort,item.storageCoverMode)
                        .getUrl(tenant,item.storageCoverKey);
        return new LibraryItemVo(
                item.id,
                item.type,
                item.source.name(),
                item.filename,
                item.sizeBytes,
                item.imageMode,
                item.durationSec,
                item.category,
                item.createdAt == null ? Instant.now().toString() : item.createdAt.toString(),
                item.used,
                item.reviewBadge,
                item.materialId,
                item.objectId,
                poster,
                play,
                item.width,
                item.height,
                (item.id.startsWith("UPLOAD:")||item.id.startsWith("ENGINE:")) && item.carouselId==null ? item.objectId : null,
                item.aiReview!=null ? "STORED" : null,
                item.syncs,item.aiReview,item.carouselId);
    }

    private static String productIdOf(Item item) {
        if (item.id.startsWith("LYNX:")) {
            int idx = item.id.lastIndexOf(':');
            return idx < 0 ? "" : item.id.substring(idx + 1);
        }
        return "";
    }

    private static String parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "VIDEO";
        }
        try {
            String value=raw.toUpperCase(Locale.ROOT);
            if(!Set.of("VIDEO","IMAGE","CAROUSEL","AUDIO","TEXT").contains(value)) throw new IllegalArgumentException();
            return value;
        } catch (Exception ex) {
            return "VIDEO";
        }
    }

    private static LibraryResponse empty(boolean bound, int page, int size) {
        return new LibraryResponse(
                bound,
                new LibraryResponse.TabCounts(0, 0, 0),
                new LibraryResponse.StatusCounts(0, 0, 0, 0, 0),
                new LibraryResponse.PageInfo(page, size, 0),
                List.of());
    }

    private static final class Item {
        final String id;
        final String type;
        final MaterialSource source;
        final String filename;
        final long sizeBytes;
        final String imageMode;
        final Integer durationSec;
        final String category;
        final Instant createdAt;
        boolean used;
        String reviewBadge;
        final String materialId;
        final String objectId;
        final String coverImageId;
        final String posterUrl;
        final String playUrl;
        String storageObjectKey;
        String storageObjectMode;
        String storageCoverKey;
        String storageCoverMode;
        final Integer width;
        final Integer height;
        String accountId="";
        String textContent;
        String carouselId;
        com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Review aiReview;
        List<com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync> syncs=List.of();

        private Item(
                String id,
                MaterialType type,
                MaterialSource source,
                String filename,
                long sizeBytes,
                String imageMode,
                Integer durationSec,
                String category,
                Instant createdAt,
                boolean used,
                String reviewBadge,
                String materialId,
                String objectId,
                String coverImageId,
                String posterUrl,
                String playUrl,
                Integer width,
                Integer height) {
            this(id,type.name(),source,filename,sizeBytes,imageMode,durationSec,category,createdAt,used,reviewBadge,materialId,objectId,coverImageId,posterUrl,playUrl,width,height);
        }
        private Item(String id,String type,MaterialSource source,String filename,long sizeBytes,String imageMode,Integer durationSec,String category,Instant createdAt,boolean used,String reviewBadge,String materialId,String objectId,String coverImageId,String posterUrl,String playUrl,Integer width,Integer height) {
            this.id = id;
            this.type = type;
            this.source = source;
            this.filename = filename;
            this.sizeBytes = sizeBytes;
            this.imageMode = imageMode;
            this.durationSec = durationSec;
            this.category = category;
            this.createdAt = createdAt;
            this.used = used;
            this.reviewBadge = reviewBadge;
            this.materialId = materialId;
            this.objectId = objectId;
            this.coverImageId = coverImageId;
            this.posterUrl = posterUrl;
            this.playUrl = playUrl;
            this.width = width;
            this.height = height;
        }
    }
}
