package com.lynx.ad.modules.creative.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.creative.infrastructure.persistence.MaterialCreativeContextStore;
import com.lynx.ad.modules.creative.infrastructure.persistence.MaterialCreativeContextStore.Entry;
import com.lynx.ad.modules.delivery.application.CreationPayloadBuilder;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Scene analysis is asynchronous and never changes material readiness or sends an ad write. */
@Service
public class MaterialCreativeContextService {
    private final MaterialCreativeContextStore store;private final ObjectMapper json;
    private final CreationEngineClient engine;private final AssetUploadService assets;
    private final CarouselPlatformService carousels;private final CreationPayloadBuilder payloads;private final BillingTrialService billing;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();private final Semaphore slot=new Semaphore(1);
    private com.lynx.ad.modules.delivery.domain.port.AdManagementPort ads;
    private com.lynx.ad.modules.identity.application.QianchuanCredentialFacade credentials;
    @org.springframework.beans.factory.annotation.Autowired public void platform(com.lynx.ad.modules.delivery.domain.port.AdManagementPort ads,com.lynx.ad.modules.identity.application.QianchuanCredentialFacade credentials){this.ads=ads;this.credentials=credentials;}
    public MaterialCreativeContextService(MaterialCreativeContextStore store,ObjectMapper json,CreationEngineClient engine,AssetUploadService assets,CarouselPlatformService carousels,CreationPayloadBuilder payloads,BillingTrialService billing){this.store=store;this.json=json;this.engine=engine;this.assets=assets;this.carousels=carousels;this.payloads=payloads;this.billing=billing;}
    public static String assetKey(CreationState.Material m){return UUID.nameUUIDFromBytes((m.type+":"+switch(m.type){case "VIDEO"->m.videoAssetId;case "CAROUSEL"->m.carouselId;default->String.join(",",m.imageAssetIds);}).getBytes(StandardCharsets.UTF_8)).toString();}
    public static String identity(CreationState s,CreationState.Material m){return UUID.nameUUIDFromBytes(String.join(":",s.tenantId,s.planId,m.id,assetKey(m)).getBytes(StandardCharsets.UTF_8)).toString();}
    public void record(CreationState s,CreationState.Material m){
        if(!"READY".equals(m.status))return;
        String id=identity(s,m);if(store.get(s.tenantId,id).isPresent())return;
        var body=json.createObjectNode().put("origin",m.engineMaterialId==null?"LIBRARY":"AI").put("format",m.type);
        if(m.engineMaterialId!=null)body.put("engine_material_id",m.engineMaterialId);
        if(m.generationDescription!=null)body.put("user_description",m.generationDescription);
        var refs=body.putArray("asset_ids");if("VIDEO".equals(m.type))refs.add(m.videoAssetId);else m.imageAssetIds.forEach(refs::add);
        var keys=body.putArray("platform_keys");
        if("VIDEO".equals(m.type))keys.add("VIDEO:"+payloads.uploaded(s,m.videoAssetId,"VIDEO").objectId());
        else if("IMAGE".equals(m.type))for(String asset:m.imageAssetIds)keys.add("IMAGE:"+payloads.uploaded(s,asset,"IMAGE").objectId());
        else if("CAROUSEL".equals(m.type))keys.add("CAROUSEL:"+carousels.ensure(new TenantId(s.tenantId),m.carouselId,s.accountId));
        var reused=store.reusable(s.tenantId,m.productId,assetKey(m));
        store.insert(new Entry(id,s.tenantId,s.planId,s.accountId,m.productId,m.id,assetKey(m),reused.isPresent()?"READY":"PENDING",body,reused.map(Entry::profile).orElse(null),false,null,1,0));
    }
    public void hydrate(CreationState s){for(var m:s.materials)hydrate(s,m);}
    public void hydrate(CreationState s,CreationState.Material m){m.creativeProfile=null;m.creativeProfileStatus=null;store.get(s.tenantId,identity(s,m)).ifPresent(e->{m.creativeProfile=e.profile();m.creativeProfileStatus=e.status();});}
    /** Called only after the gateway verified current plan/product/object membership. */
    public void recordPlatform(com.lynx.ad.modules.delivery.domain.model.AdPlan plan,JsonNode row){
        if(!"MATERIAL".equals(row.path("kind").asText())||row.path("object_key").asText().isBlank())return;
        String product=row.path("product_id").asText(),key=row.path("object_key").asText(),type=row.path("material_type").asText();
        if(!Set.of("VIDEO","IMAGE","CAROUSEL").contains(type)||!key.startsWith(type+":"))return;
        for(var e:store.history(plan.tenantId().value(),plan.accountId().value()))if(e.product().equals(product))for(var bound:e.body().path("platform_keys"))if(key.equals(bound.asText()))return;
        String asset=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        String id=UUID.nameUUIDFromBytes(String.join(":",plan.tenantId().value(),plan.accountId().value(),plan.id().value(),product,key).getBytes(StandardCharsets.UTF_8)).toString();
        var body=json.createObjectNode().put("format",type).put("origin","PLATFORM_LIBRARY").put("platform_material_id",row.path("id").asText());body.putArray("platform_keys").add(key);
        store.insert(new Entry(id,plan.tenantId().value(),plan.id().value(),plan.accountId().value(),product,row.path("id").asText(),asset,"PENDING",body,null,false,null,1,0));
    }
    public void retry(CreationState s,CreationState.Material m){record(s,m);store.retry(s.tenantId,identity(s,m));}
    public void removed(CreationState s,CreationState.Material m){store.removed(s.tenantId,s.planId,m.id,"MANUAL");}
    public void confirmedRemoval(String tenant,String account,Set<String> plans,String product,String key){
        for(var e:store.history(tenant,account))if(plans.contains(e.plan())&&product.equals(e.product()))for(var bound:e.body().path("platform_keys"))if(key.equals(bound.asText()))store.removed(tenant,e.plan(),e.material(),"PLATFORM_CONFIRMED");
    }
    public List<Entry> history(String tenant,String account){return store.history(tenant,account);}
    public void attach(String tenant,String account,ArrayNode entities,ObjectNode creative){
        var history=store.history(tenant,account);var archive=creative.putArray("scene_history");
        for(var e:history){var saved=archive.addObject().put("context_id",e.id()).put("product_id",e.product()).put("material_id",e.material()).put("origin",e.body().path("origin").asText()).put("status",e.status()).put("removed",e.removed());if(e.removalReason()!=null)saved.put("removal_reason",e.removalReason());if(e.profile()!=null)saved.set("creative_profile",e.profile());if(e.body().has("assessment"))saved.set("assessment",e.body().get("assessment"));
            if(e.profile()==null)continue;
            for(var row:entities)if("MATERIAL".equals(row.path("kind").asText())&&e.product().equals(row.path("product_id").asText()))
                for(var key:e.body().path("platform_keys"))if(!key.asText().isBlank()&&key.asText().equals(row.path("object_key").asText())){
                    var features=(ObjectNode)row.path("creative_features");if(features.has("creative_profile"))continue;features.set("creative_profile",e.profile());features.put("profile_status",e.status()).put("context_id",e.id());
                    for(String field:List.of("visual_style","creative_angle","hook"))if(!e.profile().path(field).asText().isBlank())features.set(field,e.profile().get(field));
                }
        }
    }
    public void saveAssessments(String tenant,String account,JsonNode result){if(!tenant.equals(result.path("store_id").asText())||!account.equals(result.path("account_id").asText()))throw new IllegalStateException("Assessment tenant mismatch");for(var assessment:result.path("creative_direction_assessments"))if(assessment.hasNonNull("context_id"))store.assessment(tenant,account,assessment.path("context_id").asText(),assessment.path("product_id").asText(),assessment);}
    @Scheduled(fixedDelayString="${lynx.ad.creation.context-poll-ms:10000}") public void poll(){
        if(!slot.tryAcquire())return;Entry claimed=null;
        try{for(var e:store.pending())if(store.claim(e)){claimed=e;break;}}catch(RuntimeException e){slot.release();throw e;}
        if(claimed==null){slot.release();return;}Entry e=claimed;
        executor.submit(()->{try{process(e);}catch(RuntimeException failure){store.defer(e);}finally{slot.release();}});
    }
    public void process(Entry e){
        billing.assertWritable(new TenantId(e.tenant()),Instant.now(),WriteAction.SAVE_PLAN);
        JsonNode plan=null;
        if(e.body().hasNonNull("engine_material_id")){
            plan=engine.find("/api/v1/capabilities/perception/materials/"+CreationEngineClient.query(e.body().path("engine_material_id").asText())+"/profile?store_id="+CreationEngineClient.query(e.tenant()));
            if(plan!=null){if(plan.path("product_id").asText().isBlank())plan=null;else checkScope(e,plan);}
        }
        String request=e.id()+"-"+e.version(),path="/api/v1/capabilities/perception/material-contexts";
        var receipt=engine.find(path+"/"+request+"?store_id="+CreationEngineClient.query(e.tenant()));
        if(receipt==null){var urls=new ArrayList<String>();for(var ref:e.body().path("asset_ids")){var detail=assets.detail(new TenantId(e.tenant()),ref.asText());if(detail.url()==null||detail.url().isBlank())throw new IllegalStateException("Media URL unavailable");urls.add(detail.url());}
            if(e.body().hasNonNull("platform_material_id"))urls.addAll(platformUrls(e));
            receipt=engine.post(path,Map.of("store_id",e.tenant(),"request_id",request,"product_id",e.product(),"format",e.body().path("format").asText(),"asset_key",e.assetKey(),"media_urls",urls.stream().limit(8).toList()));}
        if(!request.equals(receipt.path("request_id").asText()))throw new IllegalStateException("Creative receipt mismatch");
        if("READY".equals(receipt.path("status").asText())){var profile=receipt.path("profile");checkScope(e,profile);var result=withDescription(e,profile);if(plan!=null)result.set("generation_plan",plan);store.finish(e,"READY",result);}
        else if("FAILED".equals(receipt.path("status").asText()))store.finish(e,"FAILED",plan==null?null:withDescription(e,plan));
        else store.defer(e);
    }
    private static void checkScope(Entry e,JsonNode p){if(!e.product().equals(p.path("product_id").asText())||!e.body().path("format").asText().equals(p.path("format").asText()))throw new IllegalStateException("Creative profile scope mismatch");}
    private ObjectNode withDescription(Entry e,JsonNode p){var result=(ObjectNode)p.deepCopy();if(e.body().hasNonNull("user_description"))result.set("user_description",e.body().get("user_description"));return result;}
    private List<String> platformUrls(Entry e){
        var c=credentials.getUsableCredential(new TenantId(e.tenant()),new com.lynx.ad.common.domain.Ids.AccountId(e.account()));String id=e.body().path("platform_material_id").asText(),type=e.body().path("format").asText();
        var metadata=ads.materialMetadata(c,type,List.of(id)).stream().filter(r->id.equals(r.path("material_id").asText())).findFirst().orElseThrow(()->new IllegalStateException("Platform media metadata unavailable"));
        var urls=new ArrayList<String>();if("CAROUSEL".equals(type)){for(var image:metadata.path("images"))if(!image.path("url").asText().isBlank())urls.add(image.path("url").asText());}
        else if(!metadata.path("url").asText().isBlank())urls.add(metadata.path("url").asText());
        if(urls.isEmpty())throw new IllegalStateException("Platform media URL unavailable");return urls;
    }
    @PreDestroy public void stop(){executor.shutdownNow();}
}
