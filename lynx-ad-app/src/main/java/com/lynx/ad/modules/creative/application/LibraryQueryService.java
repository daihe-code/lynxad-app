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

    private final CreativeTaskRepository repository;
    private final StoragePort storagePort;
    private final MaterialLibraryPort materialLibraryPort;
    private final OauthQueryService oauthQueryService;
    private final PlanCreativeBindingQuery bindingQuery;
    private final ProductQueryService productQueryService;

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
        MaterialType type = parseType(typeRaw);
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? today.minusDays(6) : from;
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
        if (bound) {
            try {
                merged.addAll(platformItems(type, start, end));
            } catch (BizException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BizException(1506, "素材库查询失败");
            }
        }
        merged = dedup(merged);
        Set<String> usedKeys = bindingQuery.usedKeys(tenantId);
        for (Item item : merged) {
            item.used = used(item, usedKeys);
        }
        LibraryResponse.TabCounts tabs = tabs(merged, start, end);
        List<Item> typed = merged.stream()
                .filter(i -> i.type == type)
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
        List<LibraryItemVo> pageItems = filtered.subList(fromIdx, toIdx).stream().map(this::toVo).toList();
        return new LibraryResponse(
                bound, tabs, statuses, new LibraryResponse.PageInfo(pg, size, filtered.size()), pageItems);
    }

    public List<LibraryVideoVo> libraryVideos(TenantId tenantId) {
        List<Item> merged = new ArrayList<>(localItems(tenantId));
        if (oauthQueryService.hasBoundQianchuan(tenantId)) {
            try {
                merged.addAll(platformItems(MaterialType.VIDEO, LocalDate.of(1970, 1, 1), LocalDate.of(2099, 12, 31)));
            } catch (BizException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BizException(1506, "素材库查询失败");
            }
        }
        return dedup(merged).stream()
                .filter(i -> i.type == MaterialType.VIDEO)
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
                if (!art.productId().value().equals(e.getKey())) {
                    continue;
                }
                Instant created = art.createdAt() != null ? art.createdAt() : pkg.createdAt();
                String play = art.videoObjectKey() == null ? "" : storagePort.getUrl(tenantId, art.videoObjectKey());
                String poster = art.coverObjectKey() == null ? play : storagePort.getUrl(tenantId, art.coverObjectKey());
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
                        poster,
                        play,
                        null,
                        null));
            }
        }
        return items;
    }

    private List<Item> platformItems(MaterialType requested, LocalDate start, LocalDate end) {
        List<Item> items = new ArrayList<>();
        for (MaterialType type : MaterialType.values()) {
            var page = materialLibraryPort.list(new MaterialLibraryPort.MaterialLibraryQuery(
                    "bound", type, start, end, 1, 20));
            for (var m : page.items()) {
                items.add(new Item(
                        "EXT:" + type.name() + ":" + m.objectId(),
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
            }
        }
        return items;
    }

    private List<Item> dedup(List<Item> items) {
        Map<String, Item> byId = new HashMap<>();
        for (Item item : items) {
            String key = item.materialId != null ? item.materialId : item.objectId;
            Item exist = byId.get(key);
            if (exist == null || exist.source == MaterialSource.EXTERNAL && item.source == MaterialSource.LYNX) {
                byId.put(key, item);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private LibraryResponse.TabCounts tabs(List<Item> merged, LocalDate start, LocalDate end) {
        int v = 0, i = 0, c = 0;
        for (Item item : merged) {
            if (!inRange(item.createdAt, start, end)) {
                continue;
            }
            switch (item.type) {
                case VIDEO -> v++;
                case IMAGE -> i++;
                case CAROUSEL -> c++;
            }
        }
        return new LibraryResponse.TabCounts(v, i, c);
    }

    private static boolean used(Item item, Set<String> keys) {
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
        return contains(item.filename, needle) || contains(item.materialId, needle) || contains(item.objectId, needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private LibraryItemVo toVo(Item item) {
        return new LibraryItemVo(
                item.id,
                item.type.name(),
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
                item.posterUrl,
                item.playUrl,
                item.width,
                item.height);
    }

    private static String productIdOf(Item item) {
        if (item.id.startsWith("LYNX:")) {
            int idx = item.id.lastIndexOf(':');
            return idx < 0 ? "" : item.id.substring(idx + 1);
        }
        return "";
    }

    private static MaterialType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return MaterialType.VIDEO;
        }
        try {
            return MaterialType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return MaterialType.VIDEO;
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
        final MaterialType type;
        final MaterialSource source;
        final String filename;
        final long sizeBytes;
        final String imageMode;
        final Integer durationSec;
        final String category;
        final Instant createdAt;
        boolean used;
        final String reviewBadge;
        final String materialId;
        final String objectId;
        final String coverImageId;
        final String posterUrl;
        final String playUrl;
        final Integer width;
        final Integer height;

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
