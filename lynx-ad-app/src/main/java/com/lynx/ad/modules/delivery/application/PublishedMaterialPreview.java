package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.infrastructure.persistence.CarouselRepository;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanCreativeBindings;
import java.util.*;
import org.springframework.stereotype.Component;

/** Projects owned previews onto the requested physical plan without rewriting its audit history. */
@Component
public class PublishedMaterialPreview {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PublishedMaterialPreview.class);
    private final AdPlanRepository plans;
    private final CreationQianchuanGateway gateway;
    private final CarouselRepository carousels;
    public PublishedMaterialPreview(AdPlanRepository plans,CreationQianchuanGateway gateway,CarouselRepository carousels) {
        this.plans=plans;this.gateway=gateway;this.carousels=carousels;
    }
    public void project(TenantId tenant,String requested,CreationState state,Map<String,AssetUploadService.Detail> files) {
        if(!"CREATED".equals(state.publishStatus)||state.publishedEdit!=null)return;
        state.publishedMaterialsCurrent=false;
        try {
            var plan=plans.findById(tenant,new PlanId(requested)).orElseThrow(()->new BizException(1405,"计划不存在"));
            if(plan.qcPlanId().isEmpty()||!plan.accountId().value().equals(state.accountId)||!plan.awemeId().value().equals(state.awemeId))return;
            var live=gateway.detail(tenant,state.accountId,state.awemeId,plan.qcPlanId().orElseThrow().value());
            if(!plan.qcPlanId().orElseThrow().value().equals(live.path("ad_id").asText()))return;
            var groups=QianchuanCreativeBindings.groups(live);
            var keys=new LinkedHashMap<String,Set<String>>();
            var pools=new LinkedHashMap<String,CreationState.TitlePool>();
            Boolean hidden=null;
            for(var group:groups) {
                String product=group.path("product_id").asText();
                if(state.products.stream().noneMatch(p->p.qcProductId().equals(product)))return;
                if(!group.path("block_video_material").isEmpty())return;
                boolean visibility=group.path("hide_in_aweme").asBoolean(true);
                if(hidden!=null&&hidden!=visibility)return;hidden=visibility;
                keys.put(product,PublishedMaterialService.objects(group));
                var pool=new CreationState.TitlePool();
                var old=state.titlePools.get(product);
                for(var title:group.path("title_material")) {
                    String text=title.path("title").asText();
                    boolean card="COMMODITY_CARD".equals(title.path("title_type").asText());
                    (card?pool.cardTitles:pool.customTitles).add(text);
                    if(old!=null&&(card?old.cardGeneratedTitles:old.customGeneratedTitles).contains(text))
                        (card?pool.cardGeneratedTitles:pool.customGeneratedTitles).add(text);
                }
                if(old!=null){pool.customTextAssetId=old.customTextAssetId;pool.cardTextAssetId=old.cardTextAssetId;pool.provenanceRecorded=old.provenanceRecorded;}
                pools.put(product,pool);
            }
            var selected=new ArrayList<CreationState.Material>();
            var matched=new HashMap<String,Set<String>>();
            for(var material:state.activeMaterials()) {
                var objects=objects(state,material,files);
                if(objects.isEmpty()||!keys.getOrDefault(material.productId,Set.of()).containsAll(objects))continue;
                if(matched.getOrDefault(material.productId,Set.of()).containsAll(objects))continue;
                // A changed cover is also a changed preview, even when the video ID stayed the same.
                if("VIDEO".equals(material.type)) {
                    String cover=object(state,material.coverAssetId,files);
                    JsonNode group=null;for(var item:groups)if(material.productId.equals(item.path("product_id").asText()))group=item;
                    if(group==null)continue;
                    boolean same=false;
                    for(var video:group.path("video_material"))if(objects.contains("VIDEO:"+video.path("video_id").asText())&&Objects.equals(cover,video.path("video_cover_id").asText(null)))same=true;
                    if(!same)continue;
                }
                selected.add(material);matched.computeIfAbsent(material.productId,k->new HashSet<>()).addAll(objects);
            }
            // Unowned/new platform media use the live platform preview instead of an incomplete local list.
            if(keys.entrySet().stream().anyMatch(e->!e.getValue().equals(matched.getOrDefault(e.getKey(),Set.of()))))return;
            var alternatives=state.materials.stream().filter(CreationState.Material::pendingVersion).toList();
            state.materials=new ArrayList<>(selected);state.materials.addAll(alternatives);
            state.products=new ArrayList<>(state.products.stream().filter(p->keys.containsKey(p.qcProductId())).toList());
            state.titlePools=pools;state.hideInAweme=Boolean.TRUE.equals(hidden);
            for(var material:selected) {
                var pool=pools.get(material.productId);
                material.titles=new ArrayList<>("IMAGE".equals(material.type)?pool.cardTitles:pool.customTitles);
            }
            state.publishedMaterialsCurrent=true;
        }catch(BizException unavailable) {
            // The platform-backed preview owns its own retry UI. A saved local list is not current evidence.
        }catch(RuntimeException unavailable) {
            log.warn("Cannot project current material previews for plan {}",requested,unavailable);
        }
    }
    private Set<String> objects(CreationState state,CreationState.Material material,Map<String,AssetUploadService.Detail> files) {
        var result=new HashSet<String>();
        if("VIDEO".equals(material.type)) {String id=object(state,material.videoAssetId,files);if(id!=null)result.add("VIDEO:"+id);}
        else if("IMAGE".equals(material.type))for(String asset:material.imageAssetIds){String id=object(state,asset,files);if(id==null)return Set.of();result.add("IMAGE:"+id);}
        else if("CAROUSEL".equals(material.type)&&material.carouselId!=null) {
            var bundle=carousels.require(state.tenantId,material.carouselId);
            carousels.platformReceipts(bundle).stream().filter(r->state.accountId.equals(r.accountId())).forEach(r->result.add("CAROUSEL:"+r.objectId()));
        }
        return result;
    }
    private static String object(CreationState state,String asset,Map<String,AssetUploadService.Detail> files) {
        var file=asset==null?null:files.get(asset);if(file==null)return null;
        return file.syncs().stream().filter(s->state.accountId.equals(s.accountId())&&"UPLOADED".equals(s.status())).map(s->s.objectId()).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
