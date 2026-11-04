package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import java.util.*;
import org.springframework.stereotype.Service;

/** Deletes library membership, retaining source files and every existing plan binding. */
@Service
public class LibraryDeletionService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LibraryDeletionService.class);
    private final AssetUploadRepository assets;
    private final CarouselRepository carousels;
    private final CreativeTaskRepository packages;
    private final PlatformMaterialCatalog catalog;
    private final LibraryDeletionRepository deletions;
    private final QianchuanCredentialFacade credentials;
    private final MaterialLibraryPort library;

    public LibraryDeletionService(AssetUploadRepository assets, CarouselRepository carousels,
            CreativeTaskRepository packages, PlatformMaterialCatalog catalog, LibraryDeletionRepository deletions,
            QianchuanCredentialFacade credentials, MaterialLibraryPort library) {
        this.assets=assets; this.carousels=carousels; this.packages=packages; this.catalog=catalog;
        this.deletions=deletions; this.credentials=credentials; this.library=library;
    }

    public record Failure(String id, String message) {}
    public record Result(List<String> deletedIds, List<Failure> failures) {}
    private record Remote(String account, String advertiser, MaterialType type, String object, String material) {
        List<String> aliases() {
            var keys = new ArrayList<String>();
            keys.add(LibraryDeletionRepository.remote(account,type.name(),"o:"+object));
            keys.add(LibraryDeletionRepository.remote(account,type.name(),object)); // Legacy catalog identity.
            if (present(material)) keys.add(LibraryDeletionRepository.remote(account,type.name(),"m:"+material));
            return keys;
        }
    }
    private record Target(String id, List<Remote> remotes) {}
    private record Group(String account, String advertiser, MaterialType type) {}

    public Result delete(TenantId tenant, List<String> ids, String account) {
        if (ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(id -> !present(id) || id.length()>320))
            throw new BizException(1520,"请选择 1-100 项素材");
        // Resolve the entire request before issuing provider mutations. Never trust browser-supplied file IDs.
        Set<String> hidden=deletions.hidden(tenant.value());
        var targets=new ArrayList<Target>(); var deleted=new ArrayList<String>();
        for (String id : new LinkedHashSet<>(ids)) {
            if (hidden.contains(id)) deleted.add(id);
            else targets.add(resolve(tenant,id,account));
        }
        var groups=new LinkedHashMap<Group,Set<Remote>>();
        for (var target:targets) for (var remote:target.remotes()) {
            if (hidden.contains(remote.aliases().getFirst())) continue;
            groups.computeIfAbsent(new Group(remote.account(),remote.advertiser(),remote.type()), ignored -> new LinkedHashSet<>()).add(remote);
        }
        var failed=new HashSet<Remote>();
        for (var group:groups.entrySet()) {
            var key=group.getKey(); var remotes=new ArrayList<>(group.getValue());
            for (int start=0;start<remotes.size();start+=100) {
                var batch=remotes.subList(start,Math.min(start+100,remotes.size()));
                try {
                    var c=credentials.getUsableCredential(tenant,new AccountId(key.account()));
                    if (!c.advertiserId().value().equals(key.advertiser()))
                        throw new BizException(1523,"广告账户已变更，请刷新后重试");
                    var failedObjects=library.delete(new MaterialLibraryPort.DeleteQuery(key.advertiser(),key.type(),
                            batch.stream().map(Remote::object).distinct().toList(),c.accessToken(),key.account()));
                    var confirmed=batch.stream().filter(r -> !failedObjects.contains(r.object())).toList();
                    var aliases=confirmed.stream().flatMap(r -> r.aliases().stream()).toList();
                    deletions.hide(tenant.value(),aliases); hidden.addAll(aliases);
                    batch.stream().filter(r -> failedObjects.contains(r.object())).forEach(failed::add);
                } catch (RuntimeException unavailable) {
                    log.warn("Library deletion unconfirmed: account={}, type={}, count={}, failure={}",key.account(),key.type(),batch.size(),
                            unavailable instanceof BizException e ? "code:"+e.code() : unavailable.getClass().getSimpleName());
                    failed.addAll(batch);
                }
            }
        }
        var failures=new ArrayList<Failure>();
        for (var target:targets) {
            if (target.remotes().stream().anyMatch(failed::contains)) {
                failures.add(new Failure(target.id(),"暂时无法删除，请重试"));
            } else {
                deletions.hide(tenant.value(),List.of(target.id())); deleted.add(target.id());
            }
        }
        return new Result(List.copyOf(deleted),List.copyOf(failures));
    }

    private Target resolve(TenantId tenant,String id,String account) {
        String[] parts=id.split(":",4);
        if (parts.length==2 && Set.of("UPLOAD","ENGINE").contains(parts[0])) {
            var asset=assets.require(tenant.value(),parts[1]);
            if ("ENGINE".equals(parts[0]) != "engine_oss".equals(asset.storageMode())) throw invalid();
            var remotes=new ArrayList<Remote>();
            for (var sync:assets.syncs(tenant.value(),asset.id())) {
                if (present(account) && !account.equals(sync.accountId())) continue;
                if (Set.of("RUNNING","QUEUED","UNCERTAIN").contains(sync.status()))
                    throw new BizException(1523,"素材正在处理中，请稍后删除");
                if ("UPLOADED".equals(sync.status()) && Set.of("VIDEO","IMAGE").contains(asset.kind())) {
                    if (!present(sync.objectId()) || !present(sync.advertiserId())) throw invalid();
                    remotes.add(new Remote(sync.accountId(),sync.advertiserId(),MaterialType.valueOf(asset.kind()),sync.objectId(),sync.materialId()));
                }
            }
            return new Target(id,remotes);
        }
        if (parts.length==2 && "CAROUSEL".equals(parts[0])) {
            carousels.require(tenant.value(),parts[1]);
            // There is no file-library carousel deletion API. Keep shared image/audio components intact.
            return new Target(id,List.of());
        }
        if (parts.length==4 && "EXT".equals(parts[0])) {
            if (present(account) && !account.equals(parts[1])) throw invalid();
            MaterialType type;
            try { type=MaterialType.valueOf(parts[2]); } catch (IllegalArgumentException ex) { throw invalid(); }
            var entry=catalog.find(tenant.value(),parts[1],type,parts[3]).orElseThrow(LibraryDeletionService::invalid);
            return new Target(id,type==MaterialType.CAROUSEL ? List.of()
                    : List.of(new Remote(entry.accountId(),entry.advertiserId(),type,entry.objectId(),entry.materialId())));
        }
        if (parts.length==3 && "LYNX".equals(parts[0])) {
            var pkg=packages.findPackage(tenant,new PackageId(parts[1])).orElseThrow(LibraryDeletionService::invalid);
            var art=pkg.items().stream().filter(a -> !a.mockContent() && a.productId().value().equals(parts[2])).findFirst().orElseThrow(LibraryDeletionService::invalid);
            if (art.videoId().isEmpty()) return new Target(id,List.of());
            if (!present(account)) throw new BizException(1523,"请先选择投放账户");
            var c=credentials.getUsableCredential(tenant,new AccountId(account));
            return new Target(id,List.of(new Remote(account,c.advertiserId().value(),MaterialType.VIDEO,art.videoId().orElseThrow().value(),null)));
        }
        throw invalid();
    }

    private static boolean present(String value) { return value!=null && !value.isBlank(); }
    private static BizException invalid() { return new BizException(1522,"素材不存在或不属于当前账户，请刷新后重试"); }
}
