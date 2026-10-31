package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.*;

/** Selection identity belongs to the media file, not the row ID or advertising title. */
final class CreationMaterialSelections {
    private final AssetUploadService assets;
    private final TenantId tenant;
    private final Map<String,AssetUploadService.Detail> cache=new HashMap<>();
    private final Set<String> unavailable=new HashSet<>();
    CreationMaterialSelections(AssetUploadService assets,TenantId tenant){this.assets=assets;this.tenant=tenant;}
    CreationMaterialSelections(AssetUploadService assets,TenantId tenant,Map<String,AssetUploadService.Detail> hydrated){this(assets,tenant);cache.putAll(hydrated);}

    private record Key(String product,String type,List<String> files) {}
    private String file(String id) {
        if(id==null||id.isBlank())return null;
        AssetUploadService.Detail asset=cache.get(id);
        if(asset==null&&assets!=null&&!unavailable.contains(id))try {
            asset=assets.detail(tenant,id);
            if(asset!=null)cache.put(id,asset);else unavailable.add(id);
        }catch(BizException missing){unavailable.add(id);}
        if(asset==null)return "id:"+id;
        // Canonical IDs also cover uploads deduplicated by the asset service.
        if(asset.md5()!=null&&asset.md5().matches("(?i)[a-f0-9]{32}"))return "md5:"+asset.md5().toLowerCase(Locale.ROOT);
        var meta=asset.storageMetadata();
        if(meta!=null&&meta.objectKey()!=null&&!meta.objectKey().isBlank())
            return "object:"+Objects.toString(meta.endpoint(),"")+":"+Objects.toString(meta.bucket(),"")+":"+meta.objectKey();
        return "id:"+asset.id();
    }
    private Key key(CreationState.Material m) {
        if(m.productId==null||m.type==null||m.pendingVersion())return null;
        List<String> files=switch(m.type) {
            case "VIDEO" -> Collections.singletonList(file(m.videoAssetId));
            case "IMAGE" -> m.imageAssetIds.stream().map(this::file).toList();
            case "CAROUSEL" -> {
                if(m.carouselId!=null&&!m.carouselId.isBlank())yield List.of("carousel:"+m.carouselId);
                if(m.imageAssetIds.isEmpty()||m.audioAssetId==null)yield Collections.singletonList(null);
                var parts=new ArrayList<>(m.imageAssetIds.stream().map(this::file).toList());parts.add(file(m.audioAssetId));yield parts;
            }
            default -> List.of();
        };
        return files.isEmpty()||files.stream().anyMatch(Objects::isNull)?null:new Key(m.productId,m.type,files);
    }
    boolean contains(CreationState state,CreationState.Material selection) {
        var key=key(selection);
        return key!=null&&state.activeMaterials().stream().anyMatch(m->key.equals(key(m)));
    }
    Set<String> duplicateIds(CreationState state) {
        var kept=new LinkedHashMap<Key,CreationState.Material>();var duplicates=new LinkedHashSet<String>();
        for(var m:state.activeMaterials()) {
            // Explicit version decisions and failed rows are never silently retired.
            if(m.reviewSourceId!=null||!Set.of("READY","PROCESSING").contains(m.status))continue;
            var key=key(m);if(key==null)continue;
            var previous=kept.putIfAbsent(key,m);if(previous==null)continue;
            if(m.engineMaterialId==null)duplicates.add(m.id);
            else if(previous.engineMaterialId==null){duplicates.add(previous.id);kept.put(key,m);}
        }
        return duplicates;
    }
    static boolean repairable(CreationState s) {
        return Set.of("NONE","FAILED").contains(s.publishStatus)&&s.qcPlanId==null
                &&(s.publishedEdit==null||"DRAFT".equals(s.publishedEdit.status))
                &&!s.materialEditPending()&&!s.titleGenerationPending();
    }
}
