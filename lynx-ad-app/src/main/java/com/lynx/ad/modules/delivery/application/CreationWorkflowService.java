package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.service.ProductBasicAnalysisService;
import com.lynx.ad.modules.delivery.domain.service.CreationRecommendationPolicy;
import com.lynx.ad.modules.delivery.domain.service.CreationDraftSchedule;
import com.lynx.ad.modules.delivery.domain.service.PublishFailureRecovery;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreationWorkflowService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CreationWorkflowService.class);
    private final CreationStore store;
    private final AdPlanRepository plans;
    private final AdPlanCreativeBindingStore bindings;
    private final CreationQianchuanGateway qc;
    private final CreationEngineClient engine;
    private final ObjectMapper json;
    private final CreationAssetImporter importer;
    private final AssetUploadService assets;
    private final CarouselService carousels;
    private final CreationPayloadBuilder payloads;
    private final BillingTrialService billing;
    private final TransactionTemplate tx;
    private final CreationMaterialProcessing materialProcessing;
    private final CreationRegeneration regeneration;
    private final Object[] recommendationLocks=java.util.stream.IntStream.range(0,64).mapToObj(i->new Object()).toArray();
    private PlanTransferStore transfers;
    private MaterialCreativeContextService creativeContexts;
    private AssetSyncReconciliationService assetReconciliation;
    private GenerationResultNotice generationNotices;
    @org.springframework.beans.factory.annotation.Autowired public void generationNotices(GenerationResultNotice value){generationNotices=value;}
    private com.lynx.ad.modules.identity.application.AuthorizationCapabilityService authorizationCapabilities;
    @org.springframework.beans.factory.annotation.Autowired public void authorizationCapabilities(com.lynx.ad.modules.identity.application.AuthorizationCapabilityService value){authorizationCapabilities=value;}
    private com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore draftProducts;
    @org.springframework.beans.factory.annotation.Autowired public void draftProducts(com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore value){draftProducts=value;}
    @org.springframework.beans.factory.annotation.Autowired public void assetReconciliation(AssetSyncReconciliationService value){assetReconciliation=value;}
    @org.springframework.beans.factory.annotation.Autowired public void creativeContexts(MaterialCreativeContextService value){creativeContexts=value;}
    @org.springframework.beans.factory.annotation.Autowired public void transferStore(PlanTransferStore value){transfers=value;}
    private PublishedCompositionService compositions;
    @org.springframework.beans.factory.annotation.Autowired public void compositions(PublishedCompositionService value){compositions=value;}
    private PublishedMaterialService publishedMaterials;
    private CreationTitleRegeneration titleRegeneration;
    @org.springframework.beans.factory.annotation.Autowired public void titleRegeneration(CreationTitleRegeneration value){titleRegeneration=value;}
    @org.springframework.beans.factory.annotation.Autowired public void publishedMaterials(PublishedMaterialService value){publishedMaterials=value;}
    @org.springframework.beans.factory.annotation.Autowired public void carouselPlatform(CarouselPlatformService value){materialProcessing.carouselPlatform(value);}
    /** Products, titles and media have one canonical state for both physical mode plans. */
    private String sharedId(TenantId tenant,String id,boolean write) {
        if(id==null || id.isBlank() || transfers==null)return id;
        var g=transfers.group(tenant,new PlanId(id));if(g==null)return id;
        var member=plans.findById(tenant,new PlanId(id)).orElseThrow(()->new BizException(1403,"计划不存在或不属于当前账户"));
        var root=plans.findById(tenant,new PlanId(g.root())).orElseThrow(()->new BizException(1403,"共享素材所属计划不存在"));
        if(!member.accountId().equals(root.accountId()) || !member.awemeId().equals(root.awemeId()) || !id.equals(g.target(member.setting().bidType())))
            throw new BizException(1403,"共享素材的账户或投放方式关联不一致");
        if(write)transfers.assertIdle(tenant,new PlanId(id));
        return g.root();
    }
    public CreationWorkflowService(CreationStore store,AdPlanRepository plans,AdPlanCreativeBindingStore bindings,
            CreationQianchuanGateway qc,CreationEngineClient engine,ObjectMapper json,CreationAssetImporter importer,
            AssetUploadService assets,CarouselService carousels,CreationPayloadBuilder payloads,BillingTrialService billing,PlatformTransactionManager manager) {
        this.store=store;this.plans=plans;this.bindings=bindings;this.qc=qc;this.engine=engine;this.json=json;this.importer=importer;this.assets=assets;this.carousels=carousels;this.payloads=payloads;this.billing=billing;this.tx=new TransactionTemplate(manager);this.materialProcessing=new CreationMaterialProcessing(assets,carousels);this.regeneration=new CreationRegeneration(store,engine);
    }
    public record Selection(String planId,Integer revision,String accountId,String awemeId,List<String> productIds) {}
    public record Goal(int revision,String name,String smartBidType,BigDecimal budget,BigDecimal roi2Goal,String scheduleType,String startDate,String endDate) {}
    public record Version(int revision) {}
    public record Analysis(int revision, boolean refresh) {}
    public record Step(int revision,Integer step) {}
    public record Visibility(int revision,Boolean hideInAweme) {}
    public record Generate(int revision,List<String> productIds) {}
    public record Addition(int revision,String productId,String type,String description,String requestId) {}
    public record MaterialSelection(int revision,String productId,String type,String videoAssetId,String coverAssetId,List<String> imageAssetIds,String carouselId,List<String> titles,String addedVia) {
        public MaterialSelection(int revision,String productId,String type,String videoAssetId,String coverAssetId,List<String> imageAssetIds,String carouselId,List<String> titles){this(revision,productId,type,videoAssetId,coverAssetId,imageAssetIds,carouselId,titles,null);}
    }
    public record Feedback(int revision,List<String> reasons,String description) {}
    public record VersionDecision(int revision,String decision) {}
    public record Publish(int revision,int packageVersion,String requestKey) {}
    public record Titles(int revision,List<String> titles) {}
    public record Reconcile(String qcPlanId) {}
    public CreationState get(TenantId tenant,String requestedId) {
        String id=sharedId(tenant,requestedId,false);
        var s=store.get(tenant.value(),id);
        s=reconcileDraftSchedule(tenant,s);
        if(titleRegeneration!=null)s=titleRegeneration.refresh(s);
        // This changes only an unpublished draft's selection. Engine history,
        // OSS objects and already uploaded platform assets remain available.
        if(s.materials.stream().anyMatch(m->"product_reference".equals(m.deliverySource))) {
            var selection=json.convertValue(s,CreationState.class);
            if(selection.retireReferenceAlternatives()) {
                try{s=store.mutate(tenant.value(),id,s.revision,state->state.retireReferenceAlternatives());}
                catch(BizException conflict){if(conflict.code()!=1404)throw conflict;s=store.get(tenant.value(),id);}
            }
        }
        s=reconcileGenerationOnRead(s);
        s=refreshGeneratedFiles(tenant,s);
        s.products=new ArrayList<>(currentProductPrices(tenant,s));
        var assetDetails=materialProcessing.hydrate(tenant,s);
        var repaired=reconcileDuplicateSelections(tenant,s,assetDetails);
        if(repaired!=s){s=repaired;assetDetails=materialProcessing.hydrate(tenant,s);}
        for(var m:s.materials) {
            m.previewUrl=previewUrl(tenant,m.videoAssetId!=null?m.videoAssetId:m.audioAssetId,assetDetails);
            if("VIDEO".equals(m.type)) {
                var video=m.videoAssetId==null?null:assetDetails.get(m.videoAssetId);
                m.width=video!=null&&video.width()!=null&&video.width()>0?video.width():null;
                m.height=video!=null&&video.height()!=null&&video.height()>0?video.height():null;
                var duration=video==null||video.storageMetadata()==null?null:video.storageMetadata().durationMs();
                m.durationSec=duration!=null&&duration>0?duration/1000.0:null;
            }
            m.posterUrl=previewUrl(tenant,m.coverAssetId,assetDetails);
            m.imageUrls=new ArrayList<>();
            for(String ref:m.imageAssetIds){String url=previewUrl(tenant,ref,assetDetails);if(url!=null&&!url.isBlank())m.imageUrls.add(url);}
            if(m.posterUrl==null&&!m.imageUrls.isEmpty())m.posterUrl=m.imageUrls.getFirst();
            // Signed engine download URLs are never returned to the browser.
            m.sourceUrls.clear();m.importedAssets.clear();
        }
        if(creativeContexts!=null)creativeContexts.hydrate(s);
        return s;
    }
    private CreationState reconcileDraftSchedule(TenantId tenant,CreationState snapshot) {
        if(CreationDraftSchedule.correction(snapshot,LocalDate.now(ZoneId.of("Asia/Shanghai")))==null)return snapshot;
        try{return store.mutate(tenant.value(),snapshot.planId,snapshot.revision,s->{if(refreshDraftSchedule(s))s.changed();});}
        catch(BizException conflict){if(conflict.code()!=1404)throw conflict;return store.get(tenant.value(),snapshot.planId);}
    }
    private boolean refreshDraftSchedule(CreationState s) {
        var schedule=CreationDraftSchedule.correction(s,LocalDate.now(ZoneId.of("Asia/Shanghai")));
        if(schedule==null)return false;
        s.startDate=schedule.startDate().toString();s.endDate=schedule.endDate().toString();
        if(plans!=null)plans.findById(new TenantId(s.tenantId),new PlanId(s.planId))
                .filter(p->p.qcPlanId().isEmpty()&&p.status()==PlanStatus.DRAFT).ifPresent(p->{
                    var previous=p.setting();p.updateSetting(new DeliverySetting(previous.bidType(),previous.dailyBudget(),previous.roi2Goal(),schedule,previous.audience()));plans.save(p);
                });
        return true;
    }
    private CreationState reconcileDuplicateSelections(TenantId tenant,CreationState snapshot,Map<String,AssetUploadService.Detail> hydrated) {
        if(!CreationMaterialSelections.repairable(snapshot))return snapshot;
        var selections=new CreationMaterialSelections(assets,tenant,hydrated);
        if(selections.duplicateIds(snapshot).isEmpty())return snapshot;
        try{return store.mutate(tenant.value(),snapshot.planId,snapshot.revision,s->{
            if(!CreationMaterialSelections.repairable(s))return;
            var duplicates=selections.duplicateIds(s);
            if(s.materials.removeIf(m->duplicates.contains(m.id))){
                for(String id:duplicates)if(!s.removedMaterialIds.contains(id))s.removedMaterialIds.add(id);
                s.mediaChanged();
            }
        });}catch(BizException conflict){if(conflict.code()!=1404)throw conflict;return store.get(tenant.value(),snapshot.planId);}
    }
    private CreationState reconcileGenerationOnRead(CreationState s) {
        if(s.publishedEdit!=null&&"SUCCEEDED".equals(s.publishedEdit.status))return s;
        // This also repairs legacy rows with work_pending=0. The existing lease
        // prevents a page read and the background worker from importing twice.
        if(engine==null||Set.of("QUEUED","RUNNING").contains(s.analysisStatus)||!s.hasFailedGenerationSummary()
                ||System.currentTimeMillis()-s.generationReconciledAt<30000||!store.claim(s))return s;
        try {
            // Reopening a draft after the background budget expires still gets
            // one throttled result read, without restarting automatic retries.
            if(s.generationReconciliationAttempts>=10)store.mutate(s.tenantId,s.planId,null,current->{
                if(Objects.equals(current.engineJobId,s.engineJobId)&&current.hasFailedGenerationSummary())current.generationReconciliationAttempts=9;
            });
            process(s);
            return store.get(s.tenantId,s.planId);
        }catch(BizException unavailable){return store.get(s.tenantId,s.planId);}
        catch(RuntimeException unavailable){
            // The failed outcome/notice transaction has already rolled back. Optional
            // reconciliation must not prevent reading the durable plan and previews.
            log.warn("Generation reconciliation failed while reading plan {}; returning saved state",s.planId,unavailable);
            return store.get(s.tenantId,s.planId);
        }
        finally{store.release(s);}
    }
    private CreationState refreshGeneratedFiles(TenantId tenant,CreationState snapshot) {
        if(importer==null||!Set.of("NONE","FAILED").contains(snapshot.publishStatus)
                ||!Set.of("SUCCEEDED","FAILED").contains(snapshot.generationStatus))return snapshot;
        var replacements=new LinkedHashMap<String,CreationState.Material>();
        for(var old:snapshot.materials) {
            if(old.engineJobId==null||old.engineMaterialId==null)continue;
            try {
                var next=json.convertValue(old,CreationState.Material.class);
                if(old.videoAssetId!=null)next.videoAssetId=latestFile(tenant,snapshot,old,"VIDEO","VIDEO");
                if(old.coverAssetId!=null)next.coverAssetId=latestFile(tenant,snapshot,old,"COVER","IMAGE");
                if(old.audioAssetId!=null)next.audioAssetId=latestFile(tenant,snapshot,old,"AUDIO","AUDIO");
                for(int i=0;i<old.imageAssetIds.size();i++)next.imageAssetIds.set(i,latestFile(tenant,snapshot,old,"IMAGE_"+i,"IMAGE"));
                boolean changed=!Objects.equals(old.videoAssetId,next.videoAssetId)||!Objects.equals(old.coverAssetId,next.coverAssetId)
                        ||!Objects.equals(old.audioAssetId,next.audioAssetId)||!old.imageAssetIds.equals(next.imageAssetIds);
                if(!changed)continue;
                next.importedAssets.clear();
                if(next.videoAssetId!=null)next.importedAssets.put("VIDEO",next.videoAssetId);
                if(next.coverAssetId!=null)next.importedAssets.put("COVER",next.coverAssetId);
                if(next.audioAssetId!=null)next.importedAssets.put("AUDIO",next.audioAssetId);
                for(int i=0;i<next.imageAssetIds.size();i++)next.importedAssets.put("IMAGE_"+i,next.imageAssetIds.get(i));
                if("CAROUSEL".equals(next.type))next.carouselId=null;
                next.status="PROCESSING";next.error=null;
                replacements.put(old.id,next);
            }catch(BizException unavailable){/* Keep durable previews when the engine is temporarily unavailable. */}
        }
        if(replacements.isEmpty())return snapshot;
        try {
            return store.mutate(tenant.value(),snapshot.planId,snapshot.revision,state->{
                if(!Set.of("NONE","FAILED").contains(state.publishStatus))return;
                for(int i=0;i<state.materials.size();i++) {
                    var updated=replacements.get(state.materials.get(i).id);
                    if(updated!=null)state.materials.set(i,updated);
                }
                state.mediaChanged();
            });
        }catch(BizException conflict){if(conflict.code()!=1404)throw conflict;return store.get(tenant.value(),snapshot.planId);}
    }
    private String latestFile(TenantId tenant,CreationState state,CreationState.Material material,String component,String kind) {
        return importer.reference(tenant,new UserId(state.userId),state.planId+":"+material.id+":"+component,
                material.engineJobId,material.engineMaterialId,component,kind,"CAROUSEL".equals(material.type)?null:state.accountId,"CAROUSEL".equals(material.type));
    }
    private String previewUrl(TenantId tenant,String asset,Map<String,AssetUploadService.Detail> cache) {
        if(asset==null)return null;
        try{var detail=cache.computeIfAbsent(asset,id->assets.detail(tenant,id));return detail==null?null:detail.url();}catch(BizException ignored){return null;}
    }
    private List<CreationState.Product> currentProductPrices(TenantId tenant,CreationState state) {
        if(qc==null || state.products.isEmpty())return state.products;
        try {
            var next=qc.refreshPrices(tenant,state.accountId,state.awemeId,state.products);
            // A metadata refresh must never change the saved product membership.
            if(next!=null && next.stream().map(CreationState.Product::qcProductId).toList().equals(ids(state)))return next;
        }catch(BizException unavailable){/* Saved plans remain readable after authorization expires. */}
        return state.products.stream().map(p->new CreationState.Product(p.productId(),p.qcProductId(),p.name(),p.price(),p.userPrice(),p.imageUrl(),p.category(),p.stock(),p.tags(),p.unavailableReasons(),p.priceInfo()==null?null:p.priceInfo().staleCopy(),p.channelId(),p.draft(),p.sales())).toList();
    }
    public CreationState select(TenantId tenant,Selection requested) {
        var request=new Selection(sharedId(tenant,requested.planId,true),requested.revision,requested.accountId,requested.awemeId,requested.productIds);
        writable(tenant,WriteAction.SAVE_PLAN);
        var selected=qc.selected(tenant,request.accountId,request.awemeId,request.productIds);
        return tx.execute(status->{
            if(draftProducts!=null)draftProducts.assertAvailable(tenant.value(),request.accountId,request.productIds,request.planId);
            if(request.planId==null||request.planId.isBlank()) {
                var setting=new DeliverySetting(SmartBidType.CUSTOM,Money.zero(),Optional.empty(),Schedule.fromNow(),AudienceJson.empty());
                String name=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_商品全域投放_HH:mm:ss"))+"_"+UUID.randomUUID().toString().substring(0,3);
                var plan=AdPlan.draft(tenant,new AccountId(request.accountId),new AwemeId(request.awemeId),new PlanName(name),setting,request.productIds.stream().map(QcProductId::new).toList());
                plans.save(plan);bindProducts(plan);
                var s=new CreationState();s.planId=plan.id().value();s.tenantId=tenant.value();s.userId=TenantContext.userId().value();
                s.accountId=request.accountId;s.awemeId=request.awemeId;s.name=name;s.products=new ArrayList<>(selected);
                s.basicAnalysis=ProductBasicAnalysisService.analyze(s.products);store.insert(s);return s;
            }
            return store.mutate(tenant.value(),request.planId,request.revision,s->{
                selectionEditable(s);if(!s.accountId.equals(request.accountId)||!s.awemeId.equals(request.awemeId))throw new BizException(1403,"切换账户后请建立新的草稿");
                var next=selected.stream().map(CreationState.Product::qcProductId).toList();var previous=s.products.stream().map(CreationState.Product::qcProductId).toList();
                if(!ProductBasicAnalysisService.fingerprint(s.products).equals(ProductBasicAnalysisService.fingerprint(selected))) {
                    s.reachedStep=1;
                    s.groupingDecision=null;s.analysisStatus="NONE";s.analysis=null;s.analysisConfirmation=null;s.analysisRequestId=null;s.analysisStartedAt=0;s.analysisError=null;s.analysisRefreshRequested=false;
                }
                if(!new HashSet<>(previous).equals(new HashSet<>(next)))
                    com.lynx.ad.modules.delivery.domain.service.CreationProductSelection.apply(s,selected);
                if(s.engineJobId!=null&&s.generationGroupProducts.isEmpty())for(int n=0;n<s.products.size();n++)s.generationGroupProducts.put("group-"+(n+1),s.products.get(n).qcProductId());
                s.products=new ArrayList<>(selected);s.basicAnalysis=ProductBasicAnalysisService.analyze(s.products);
                if(s.publishedEdit!=null)return;
                var p=plan(s);p.replaceProducts(request.productIds.stream().map(QcProductId::new).toList());plans.save(p);bindProducts(p);
            });
        });
    }
    public CreationState analyze(TenantId tenant,String requestedId,Version v) {
        return analyze(tenant,requestedId,new Analysis(v.revision,false));
    }
    public CreationState analyze(TenantId tenant,String requestedId,Analysis v) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        var snapshot=store.get(tenant.value(),id);
        var currentProducts=currentProductPrices(tenant,snapshot);
        return store.mutate(tenant.value(),id,v.revision,s->{
            selectionEditable(s);
            boolean changed=!ProductBasicAnalysisService.fingerprint(s.products).equals(ProductBasicAnalysisService.fingerprint(currentProducts));
            s.products=new ArrayList<>(currentProducts);s.basicAnalysis=ProductBasicAnalysisService.analyze(s.products);
            if(changed){s.reachedStep=1;s.groupingDecision=null;s.analysisRequestId=null;s.analysisStatus="NONE";s.analysis=null;s.analysisConfirmation=null;s.analysisError=null;s.analysisStartedAt=0;}
            if(!s.basicAnalysis.canContinue())return;
            if(Set.of("QUEUED","RUNNING").contains(s.analysisStatus))return;
            s.analysisRefreshRequested=v.refresh;s.analysisStatus="QUEUED";s.analysisError=null;
            s.analysisRequestId=UUID.randomUUID().toString();s.analysisStartedAt=System.currentTimeMillis();s.changed();
        });
    }
    public CreationState recommendations(TenantId tenant,String requestedId) {
        return recommendations(tenant,requestedId,false);
    }
    public CreationState recommendations(TenantId tenant,String requestedId,boolean force) {
        String id=sharedId(tenant,requestedId,true);
        synchronized(recommendationLocks[Math.floorMod(Objects.hash(tenant.value(),id),recommendationLocks.length)]) {
            var s=store.get(tenant.value(),id);
            if(CreationRecommendationPolicy.reusable(s.recommendations,s,Instant.now(),force))return s;
            var response=qc.recommendations(tenant,s.accountId,s.awemeId,ids(s));
            return store.mutate(tenant.value(),id,null,state->{
                if(!CreationRecommendationPolicy.sameScope(s,state))throw new BizException(1404,"商品或账户已调整，请重新获取平台推荐");
                if(!Objects.equals(s.recommendations,state.recommendations))return;
                state.recommendations=CreationRecommendationPolicy.withLastBudget(s.recommendations,response,state,Instant.now());
                state.changed();
            });
        }
    }
    public CreationState goal(TenantId tenant,String requestedId,Goal g) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        return store.mutate(tenant.value(),id,g.revision,s->{
            editable(s);requireBasicAnalysis(s);
            if(s.publishedEdit!=null)throw new BizException(1403,"商品素材调整中沿用原投放目标，请返回计划修改页调整预算、ROI、日期或名称");
            if(g.smartBidType==null||!Set.of("CUSTOM","CONSERVATIVE","SMART_BID_CUSTOM","SMART_BID_CONSERVATIVE").contains(g.smartBidType))throw new BizException(1402,"请选择有效的投放方式");
            var previousGoal=goalProgressKey(s);int previousReached=s.reachedStep();
            s.name=DeliveryInputPolicy.text(g.name);s.smartBidType=SmartBidType.fromOcean(g.smartBidType).name();s.budget=g.budget;
            s.roi2Goal="CUSTOM".equals(s.smartBidType)?g.roi2Goal:null;s.scheduleType=g.scheduleType;s.startDate=g.startDate;s.endDate=g.endDate;
            CreationPayloadBuilder.settings(s);
            s.reachedStep=previousGoal.equals(goalProgressKey(s))?previousReached:Math.min(previousReached,2);
            var p=plan(s);var name=new PlanName(s.name);
            if(plans.existsUnfinishedName(tenant,p.awemeId(),name,Optional.of(p.id())))throw new BizException(1402,"同一抖音号已有同名计划，请修改计划名称");
            p.rename(name);p.updateSetting(new DeliverySetting(SmartBidType.fromOcean(s.smartBidType),Money.ofYuan(s.budget),Optional.ofNullable(s.roi2Goal).map(RoiGoal::new),
                    "DATE_RANGE".equals(s.scheduleType)?new Schedule(ScheduleType.DATE_RANGE,LocalDate.parse(s.startDate),LocalDate.parse(s.endDate)):Schedule.fromNow(),AudienceJson.empty()));
            plans.save(p);s.step=3;s.changed();
        });
    }
    public CreationState step(TenantId tenant,String requestedId,Step request) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        if(request.step==null||request.step<1||request.step>3)throw new BizException(1402,"草稿编辑步骤须为 1–3");
        return store.mutate(tenant.value(),id,request.revision,s->{
            if(s.publishedEdit!=null&&!"DRAFT".equals(s.publishedEdit.status))throw new BizException(1403,"保存结果待核查，请先核查原计划");
            if(s.qcPlanId!=null||!Set.of("NONE","FAILED").contains(s.publishStatus))throw new BizException(1403,"计划正在发布或已发布，不可修改草稿");
            if(request.step>=2)requireBasicAnalysis(s);
            if(request.step==2 && s.step==1)confirmAnalysisSnapshot(s);
            boolean refreshed=request.step==3&&refreshDraftSchedule(s);
            if(request.step==3&&(s.step!=3||refreshed))CreationPayloadBuilder.settings(s);
            // Navigation changes no creative content and must preserve an already confirmed package.
            int reached=s.reachedStep();
            if(s.step!=request.step||reached<request.step||refreshed){s.reachedStep=Math.max(reached,request.step);s.step=request.step;s.revision++;}
        });
    }
    public CreationState visibility(TenantId tenant,String requestedId,Visibility request) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        if(request.hideInAweme==null)throw new BizException(1402,"请选择素材可见性");
        return store.mutate(tenant.value(),id,request.revision,s->{
            editable(s);
            if(s.hideInAweme!=request.hideInAweme){s.hideInAweme=request.hideInAweme;s.mediaChanged();}
        });
    }
    private static List<Object> goalProgressKey(CreationState s) {
        boolean custom="CUSTOM".equals(s.smartBidType),dated="DATE_RANGE".equals(s.scheduleType);
        return Arrays.asList(s.smartBidType,s.budget==null?null:s.budget.stripTrailingZeros(),
                custom&&s.roi2Goal!=null?s.roi2Goal.stripTrailingZeros():null,s.scheduleType,
                dated?s.startDate:null,dated?s.endDate:null);
    }
    public CreationState generate(TenantId tenant,String requestedId,Generate g) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        if(authorizationCapabilities!=null && g.productIds!=null && !g.productIds.isEmpty())authorizationCapabilities.requireMaterials(tenant,store.get(tenant.value(),id).accountId);
        return store.mutate(tenant.value(),id,g.revision,s->{
            editable(s);refreshDraftSchedule(s);CreationPayloadBuilder.settings(s);
            requireBasicAnalysis(s);
            if(g.productIds==null||!ids(s).containsAll(g.productIds))throw new BizException(1503,"请选择当前计划中的商品生成素材");
            var decisions=new LinkedHashSet<>(s.generationSelection==null?List.<String>of():s.generationSelection);decisions.addAll(g.productIds);
            s.generationSelection=new ArrayList<>(decisions);s.generationPendingProductIds.clear();
            if(g.productIds.isEmpty()) { s.step=3;s.revision++;return; }
            s.generationGroupProducts.clear();for(int n=0;n<s.products.size();n++)s.generationGroupProducts.put("group-"+(n+1),s.products.get(n).qcProductId());
            s.generationRetry=null;s.regeneration=null;s.generationProductIds=new ArrayList<>(new LinkedHashSet<>(g.productIds));s.generationStatus="QUEUED";s.generationMessage="等待 AI 引擎生成";s.previousEngineJobId=s.engineJobId;s.engineJobId=null;s.engineTerminalStatus=null;s.engineTerminalJobId=null;s.pendingRegenerationId=null;s.progress=0;s.changed();
            s.generationStage="queued";s.generationCounts.clear();s.generationIssues.clear();s.resetGenerationReconciliation();
        });
    }
    public CreationState addition(TenantId tenant,String requestedId,Addition request) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        String description=request.description==null?"":request.description.strip();
        if(request.requestId==null||!request.requestId.matches("[A-Za-z0-9_-]{1,64}")||request.type==null||!Set.of("VIDEO","IMAGE","CAROUSEL").contains(request.type)
                ||description.codePointCount(0,description.length())>50)throw new BizException(1503,"请选择素材类型，生成描述最多50字，并提供有效请求ID");
        return store.mutate(tenant.value(),id,null,s->{
            var existing=s.generationAdditions.stream().filter(r->request.requestId.equals(r.id)).findFirst();
            if(existing.isPresent()) {
                var r=existing.get();
                if(!Objects.equals(r.productId,request.productId)||!r.type.equals(request.type)||!r.description.equals(description))throw new BizException(1404,"同一请求ID不能用于不同的生成要求");
                return;
            }
            if(authorizationCapabilities!=null)authorizationCapabilities.requireMaterials(tenant,s.accountId);
            if(s.revision!=request.revision)throw new BizException(1404,"草稿已更新，请刷新后重试");
            editable(s);refreshDraftSchedule(s);CreationPayloadBuilder.settings(s);
            requireBasicAnalysis(s);
            var product=s.products.stream().filter(p->Objects.equals(p.qcProductId(),request.productId)).findFirst().orElseThrow(()->new BizException(1503,"商品不属于当前计划"));
            if(product.imageUrl()==null||product.imageUrl().isBlank())throw new BizException(1503,"商品主图缺失，无法生成素材");
            if(s.activeMaterials().size()>=100)throw new BizException(1503,"每个计划最多使用100个素材");
            var body=new LinkedHashMap<String,Object>();body.put("store_id",s.tenantId);body.put("ai_plan_task_id",s.enginePlanId);body.put("request_id",request.requestId);
            body.put("product_id",request.productId);body.put("target",additionTarget(request.type));body.put("instruction",description);
            body.put("budget",s.budget);if(s.roi2Goal!=null)body.put("roi",s.roi2Goal);
            var p=engineProduct(product);p.put("image_urls",List.of(product.imageUrl()));body.put("product",p);
            if(creativeContexts!=null)creativeContexts.hydrate(s);
            body.put("existing_materials",s.materials.stream().filter(m->request.productId.equals(m.productId)).map(m->{var previous=new LinkedHashMap<String,Object>();previous.put("product_id",m.productId);previous.put("material_id",m.engineMaterialId==null?m.id:m.engineMaterialId);previous.put("material_type",additionTarget(m.type));previous.put("title",m.title==null?"":m.title);if(m.creativeProfile!=null)previous.put("creative_profile",m.creativeProfile);previous.put("copy_excerpt",m.text==null?"":m.text.substring(0,Math.min(500,m.text.length())));return previous;}).toList());
            for(int n=s.materials.size()-1;n>=0;n--){var m=s.materials.get(n);if(request.productId.equals(m.productId)&&m.engineJobId!=null&&m.groupId!=null){body.put("job_id",m.engineJobId);body.put("group_id",m.groupId);if(m.enginePlanId!=null){body.put("ai_plan_task_id",m.enginePlanId);s.enginePlanId=m.enginePlanId;}break;}}
            var r=new CreationState.GenerationAddition();r.id=request.requestId;r.productId=request.productId;r.type=request.type;r.description=description;r.status="QUEUED";r.createdAt=Instant.now().toString();r.engineRequest=json.valueToTree(body);
            s.regeneration=null;s.generationAdditions.add(r);s.pendingAdditionId=r.id;s.engineTerminalStatus=null;s.engineTerminalJobId=null;s.generationProductIds=new ArrayList<>(List.of(r.productId));s.generationStatus="QUEUED";s.generationMessage="等待生成新增素材";s.generationStage="queued";s.progress=0;
            s.generationCounts=new ArrayList<>(List.of(new CreationState.GenerationCount(r.type,0,1)));s.generationIssues.clear();s.resetGenerationReconciliation();s.revision++;
        });
    }
    public record RetryGeneration(int revision,String type,String productId,String slotId) {
        public RetryGeneration(int revision,String type){this(revision,type,null,null);}
    }
    public record DeleteGenerationFailure(int revision,String productId,String type,String slotId) {}
    private static CreationState.GenerationIssue failure(CreationState s,String productId,String type,String slotId) {
        if(productId==null||slotId==null||slotId.isBlank())throw new BizException(1503,"请选择失败的素材");
        return s.generationIssues.stream().filter(i->Objects.equals(productId,i.productId())&&Objects.equals(type,i.type())&&slotId.equals(i.slotId()))
                .filter(i->!s.generationIssueDismissed(i))
                .filter(i->s.materials.stream().noneMatch(m->Objects.equals(m.id,i.materialId())))
                .findFirst().orElseThrow(()->new BizException(1503,"失败项已更新，请刷新后重试"));
    }
    public CreationState deleteGenerationFailure(TenantId tenant,String requestedId,DeleteGenerationFailure request) {
        String id=sharedId(tenant,requestedId,true);writable(tenant,WriteAction.SAVE_PLAN);
        return store.mutate(tenant.value(),id,request.revision,s->{
            editable(s);
            var issue=failure(s,request.productId,request.type,request.slotId);
            s.dismissedGenerationIssues.add(new CreationState.GenerationIssueDismissal(s.engineJobId,issue.productId(),issue.type(),issue.slotId()));
            s.generationIssues.remove(issue);
            s.generationCounts.replaceAll(c->c.type().equals(issue.type())?new CreationState.GenerationCount(c.type(),c.completed(),
                    c.total()==null?null:Math.max(c.completed(),c.total()-1),Math.max(0,c.failed()-1)):c);
            s.changed();
        });
    }
    public CreationState retryGeneration(TenantId tenant,String requestedId,RetryGeneration request) {
        String id=sharedId(tenant,requestedId,true);writable(tenant,WriteAction.SAVE_PLAN);
        if(!Set.of("VIDEO","IMAGE","CAROUSEL").contains(Objects.toString(request.type,"")))throw new BizException(1503,"请选择重试的素材类型");
        if(authorizationCapabilities!=null)authorizationCapabilities.requireMaterials(tenant,store.get(tenant.value(),id).accountId);
        var reserved=store.mutate(tenant.value(),id,request.revision,s->{
            editable(s);
            if(s.engineJobId==null||s.enginePlanId==null||s.generationCounts.stream().noneMatch(c->c.type().equals(request.type)&&c.failed()>0))throw new BizException(1503,"当前类型没有已确认的失败生成项");
            var retry=new CreationState.GenerationRetry();retry.requestId=UUID.randomUUID().toString();retry.type=request.type;retry.status="SENDING";
            var issues=request.productId!=null||request.slotId!=null?List.of(failure(s,request.productId,request.type,request.slotId)):
                    s.generationIssues.stream().filter(i->request.type.equals(i.type())&&!s.generationIssueDismissed(i)&&i.slotId()!=null).toList();
            retry.targets=issues.stream().map(i->new CreationState.GenerationRetryTarget(i.productId(),i.slotId())).distinct().toList();
            if(retry.targets.isEmpty()&&s.dismissedGenerationIssues.stream().anyMatch(d->Objects.equals(s.engineJobId,d.jobId())))throw new BizException(1503,"当前没有可重试的失败项，请刷新后重试");
            s.generationRetry=retry;
            s.generationStatus="UNCERTAIN";s.generationMessage="正在提交失败项重试，成功素材已保留";s.changed();
        });
        try {
            var body=new LinkedHashMap<String,Object>();body.put("store_id",reserved.tenantId);body.put("ai_plan_task_id",reserved.enginePlanId);body.put("type",request.type);body.put("request_id",reserved.generationRetry.requestId);
            if(!reserved.generationRetry.targets.isEmpty())body.put("targets",reserved.generationRetry.targets);
            var response=engine.post("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(reserved.engineJobId)+"/retry-failed",body);
            acceptGenerationRetry(reserved,response);
        }catch(BizException error){
            if(error.code()!=8003)store.mutate(reserved.tenantId,id,null,s->{s.generationRetry.status="FAILED";s.generationStatus="FAILED";s.generationMessage=error.getMessage();});
        }
        return get(tenant,id);
    }
    private void acceptGenerationRetry(CreationState snapshot,JsonNode receipt) {
        var retry=snapshot.generationRetry;
        if(receipt==null||!snapshot.engineJobId.equals(receipt.path("job_id").asText())||!snapshot.tenantId.equals(receipt.path("store_id").asText())||!retry.type.equals(receipt.path("type").asText())||!retry.requestId.equals(receipt.path("request_id").asText()))return;
        if(!retry.targets.isEmpty()&&!json.valueToTree(retry.targets).equals(receipt.path("targets")))return;
        String status=receipt.path("status").asText();
        store.mutate(snapshot.tenantId,snapshot.planId,null,s->{
            if(s.generationRetry==null||!retry.requestId.equals(s.generationRetry.requestId))return;
            if("ACCEPTED".equals(status)){s.generationRetry.status="ACCEPTED";s.regeneration=null;s.pendingRegenerationId=null;s.engineTerminalStatus=null;s.engineTerminalJobId=null;s.generationStatus="RUNNING";s.generationMessage="失败项重试已提交，成功素材已保留";s.resetGenerationReconciliation();}
            else if("FAILED".equals(status)){s.generationRetry.status="FAILED";s.generationStatus="FAILED";s.generationMessage="失败项未能提交，请核对原任务与失败原因";}
        });
    }
    public CreationState keepProductGroup(TenantId tenant,String requestedId,Version request) {
        String id=sharedId(tenant,requestedId,true);writable(tenant,WriteAction.SAVE_PLAN);
        return store.mutate(tenant.value(),id,request.revision,s->{editable(s);requireBasicAnalysis(s);if(!"KEEP_CURRENT".equals(s.groupingDecision)){s.groupingDecision="KEEP_CURRENT";s.revision++;}});
    }
    private static String additionTarget(String type){return switch(type){case "VIDEO"->"video";case "IMAGE"->"product_card";case "CAROUSEL"->"image_text";default->throw new BizException(1503,"不支持的素材类型");};}
    private static CreationState.GenerationAddition additionRecord(CreationState s,String id){return s.generationAdditions.stream().filter(r->r.id.equals(id)).findFirst().orElseThrow(()->new BizException(1503,"新增素材任务不存在"));}
    public CreationState add(TenantId tenant,String requestedId,MaterialSelection request) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);var initial=store.get(tenant.value(),id);appendable(initial);
        if(!ids(initial).contains(request.productId))throw new BizException(1503,"素材对应商品不属于当前计划");
        if(request.addedVia!=null&&!Set.of("LIBRARY","UPLOAD").contains(request.addedVia))throw new BizException(1503,"不支持的素材添加方式");
        var m=new CreationState.Material();m.id=UUID.randomUUID().toString();m.productId=request.productId;m.type=request.type;
        m.addedVia=request.addedVia;
        m.videoAssetId=request.videoAssetId;m.coverAssetId=request.coverAssetId;m.imageAssetIds=request.imageAssetIds==null?new ArrayList<>():new ArrayList<>(request.imageAssetIds);
        m.carouselId=request.carouselId;
        var selections=new CreationMaterialSelections(assets,tenant);
        if(selections.contains(initial,m))return initial;
        if(initial.revision!=request.revision)throw new BizException(1404,"草稿已更新，请刷新后重试");
        m.titles=CreationManualSelectionPolicy.titles(initial,request.productId,request.type,request.titles).stream().filter(Objects::nonNull).map(DeliveryInputPolicy::text).filter(x->!x.isEmpty()).distinct().toList();
        DeliveryInputPolicy.titles(m.titles,true);
        m.title=m.titles.getFirst();
        List<String> targetAssets=new ArrayList<>();
        if("VIDEO".equals(m.type)){requireKind(tenant,m.videoAssetId,"VIDEO");if(m.coverAssetId==null||m.coverAssetId.isBlank())m.coverAssetId=assets.videoCover(tenant,new UserId(initial.userId),m.videoAssetId);requireKind(tenant,m.coverAssetId,"IMAGE");assets.attachPreview(tenant,m.videoAssetId,m.coverAssetId);targetAssets.addAll(List.of(m.videoAssetId,m.coverAssetId));}
        else if("IMAGE".equals(m.type)){if(m.imageAssetIds.isEmpty())throw new BizException(1503,"请选择图片");for(String a:m.imageAssetIds){requireKind(tenant,a,"IMAGE");CreationPayloadBuilder.cardImage(assets.detail(tenant,a));targetAssets.add(a);}}
        else if("CAROUSEL".equals(m.type)){var b=carousels.detail(tenant,m.carouselId);if(!Set.of("APPROVED","NOT_REQUIRED").contains(b.aiReview().status()))throw new BizException(1503,"图文质检未通过或引擎图文尚未就绪");m.imageAssetIds=b.parts().stream().filter(p->p.role().equals("IMAGE")).map(p->p.asset().id()).toList();m.audioAssetId=b.parts().stream().filter(p->p.role().equals("AUDIO")).findFirst().orElseThrow().asset().id();targetAssets.addAll(b.parts().stream().filter(p->!p.role().equals("TEXT")).map(p->p.asset().id()).toList());}
        else throw new BizException(1503,"不支持的素材类型");
        for(String a:targetAssets)assets.ensureTarget(tenant,a,initial.accountId);
        m.textAssetId=importer.ingest(tenant,new UserId(initial.userId),id+":"+m.id+":text",String.join("\n",m.titles),"TEXT",null,false);
        return store.mutate(tenant.value(),id,null,s->{appendable(s);
            // Check again under the row lock, including retries whose response was lost.
            if(selections.contains(s,m))return;
            if(s.revision!=request.revision)throw new BizException(1404,"草稿已更新，请刷新后重试");
            if(s.activeMaterials().size()>=100)throw new BizException(1503,"每个计划最多使用 100 个素材");s.materials.add(m);s.mediaChanged();});
    }
    public CreationState remove(TenantId tenant,String requestedId,String material,Feedback feedback) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        var result=store.mutate(tenant.value(),id,feedback.revision,s->{
            var m=find(s,material);
            if(m.pendingVersion()||s.materials.stream().anyMatch(v->m.id.equals(v.reviewSourceId)&&v.pendingVersion()))throw new BizException(1503,"请先处理该素材的新版本，再删除素材");
            if("CREATED".equals(s.publishStatus)&&publishedMaterials!=null) {
                publishedMaterials.reserve(tenant,requestedId,s,m,"delete");recordFeedback(s,material,"delete",feedback);s.changed();
            } else {editable(s);recordFeedback(s,material,"delete",feedback);if(creativeContexts!=null)creativeContexts.removed(s,m);s.removedMaterialIds.add(m.id);s.materials.remove(m);s.mediaChanged();}
        });
        return PublishedMaterialService.pending(result)&&publishedMaterials!=null?publishedMaterials.process(result):result;
    }
    public CreationState retryContext(TenantId tenant,String requestedId,String material){
        String id=sharedId(tenant,requestedId,false);writable(tenant,WriteAction.SAVE_PLAN);
        var s=store.get(tenant.value(),id);var m=find(s,material);
        if(!"READY".equals(m.status))throw new BizException(1503,"素材就绪后才能分析场景");
        creativeContexts.retry(s,m);return s;
    }
    public CreationState titles(TenantId tenant,String requestedId,String material,Titles request) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);var s=store.get(tenant.value(),id);editable(s);find(s,material);
        var titles=request.titles==null?List.<String>of():request.titles.stream().filter(Objects::nonNull).map(DeliveryInputPolicy::text).filter(x->!x.isBlank()).distinct().toList();
        DeliveryInputPolicy.titles(titles,true);
        String key=id+":"+material+":titles:"+hash(String.join("\n",titles));
        String text=importer.ingest(tenant,new UserId(s.userId),key,String.join("\n",titles),"TEXT",null,false);
        return store.mutate(tenant.value(),id,request.revision,state->{editable(state);var m=find(state,material);m.titles=new ArrayList<>(titles);m.title=titles.getFirst();m.textAssetId=text;m.status="PROCESSING";m.error=null;state.mediaChanged();});
    }
    public record ProductTitles(int revision,String kind,List<String> titles) {}
    public record RegenerateTitle(int revision,String kind,int index,String original) {}
    public CreationState regenerateTitle(TenantId tenant,String requestedId,String product,RegenerateTitle request) {
        String id=sharedId(tenant,requestedId,true);writable(tenant,WriteAction.SAVE_PLAN);
        var s=store.get(tenant.value(),id);editable(s);
        if(s.revision!=request.revision)throw new BizException(1404,"草稿版本已变化，请刷新后修改标题");
        if(!Set.of("CUSTOM","CARD").contains(Objects.toString(request.kind,"")))throw new BizException(1503,"标题类型不合法");
        return titleRegeneration.submit(s,product,request.kind,request.index,request.original);
    }
    /** Ad copy belongs to a product pool; editing it never reuploads or regenerates visual files. */
    public CreationState productTitles(TenantId tenant,String requestedId,String product,ProductTitles request) {
        String id=sharedId(tenant,requestedId,true);writable(tenant,WriteAction.SAVE_PLAN);
        var s=store.get(tenant.value(),id);editable(s);
        if(s.revision!=request.revision)throw new BizException(1404,"草稿版本已变化，请刷新后修改标题");
        if(s.products.stream().noneMatch(p->p.qcProductId().equals(product)))throw new BizException(1503,"商品不属于当前草稿");
        if(!Set.of("CUSTOM","CARD").contains(Objects.toString(request.kind,"")))throw new BizException(1503,"标题类型不合法");
        var titles=request.titles==null?List.<String>of():request.titles.stream().map(DeliveryInputPolicy::text).toList();
        boolean card="CARD".equals(request.kind);
        com.lynx.ad.modules.delivery.domain.service.ProductTitlePool.validate(titles,card);
        var pool=com.lynx.ad.modules.delivery.domain.service.ProductTitlePool.of(s,product);
        if((card?pool.cardTitles:pool.customTitles).equals(titles))return s;
        var all=new HashSet<>(card?pool.customTitles:pool.cardTitles);all.addAll(titles);
        if(all.size()>30)throw new BizException(1503,"每个商品最多使用30条不同标题");
        String text=importer.ingest(tenant,new UserId(s.userId),id+":"+product+":"+request.kind+":titles:"+hash(String.join("\n",titles)),String.join("\n",titles),"TEXT",null,false);
        var updated=pool;
        return store.mutate(tenant.value(),id,request.revision,state->{
            editable(state);
            com.lynx.ad.modules.delivery.domain.service.ProductTitlePool.generated(updated,card).retainAll(titles);
            if(card){updated.cardTitles=new ArrayList<>(titles);updated.cardTextAssetId=text;}
            else{updated.customTitles=new ArrayList<>(titles);updated.customTextAssetId=text;}
            state.titlePools.put(product,updated);state.mediaChanged();
        });
    }
    public CreationState recheck(TenantId tenant,String requestedId,String material,Version v) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        return store.mutate(tenant.value(),id,v.revision,s->{editable(s);var m=find(s,material);m.status="PROCESSING";m.error=null;s.mediaChanged();});
    }
    /** Requeue only definite failed component uploads; never acknowledge an uncertain receipt or call generation. */
    public record MaterialSyncVerification(String materialId,int revision,String state,String message,MaterialProcessing processing) {}
    /** Read platform receipts only. In particular, do not call get(): it can import/generate on read. */
    public MaterialSyncVerification verifyMaterialSync(TenantId tenant,String requestedId,String material,Version version) {
        var initial=store.get(tenant.value(),sharedId(tenant,requestedId,false));
        if(initial.revision!=version.revision)throw new BizException(1404,"草稿已更新，请读取当前草稿后再次核查");
        var selected=find(initial,material);
        var refs=new LinkedHashSet<String>(selected.importedAssets.values());
        refs.add(selected.videoAssetId);refs.add(selected.coverAssetId);refs.add(selected.audioAssetId);refs.addAll(selected.imageAssetIds);
        if(selected.carouselId!=null&&carousels!=null)for(var part:carousels.detail(tenant,selected.carouselId).parts())if(!"TEXT".equals(part.role()))refs.add(part.asset().id());
        record Candidate(String asset,com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync sync) {}
        var candidates=new ArrayList<Candidate>();
        for(String ref:refs) {
            if(ref==null||ref.isBlank())continue;
            var detail=assets.detail(tenant,ref);if("TEXT".equals(detail.kind()))continue;
            detail.syncs().stream().filter(s->initial.accountId.equals(s.accountId())&&"UNCERTAIN".equals(s.status())).findFirst().ifPresent(s->candidates.add(new Candidate(ref,s)));
        }
        // One bounded metadata request per action; cycle through components rather than scanning an account.
        candidates.sort(Comparator.comparing(c->c.sync().verification()==null||c.sync().verification().checkedAt()==null?Instant.EPOCH:c.sync().verification().checkedAt()));
        String state="NOT_REQUIRED",message="当前没有待确认的文件上传，本次没有上传或生成请求。";
        if(!candidates.isEmpty()) {
            var next=candidates.getFirst();
            var result=assetReconciliation.reconcile(tenant,next.asset(),initial.accountId);
            state=result.state();message=result.message();
            if(candidates.size()>1)message+=" 本次核查一个文件组件，其他组件可继续核查。";
        }
        var progress=materialProcessing.inspect(initial,selected).progress();
        if(candidates.isEmpty()&&progress.components().stream().anyMatch(c->"CAROUSEL".equals(c.component())&&"UNCERTAIN".equals(c.syncStatus()))) {
            state="INCOMPLETE";message="文件组件已读取；图文组合创建缺少可核验的组合 ID，保留待确认状态，不会重复创建。";
        }else if(candidates.isEmpty()&&Set.of("UNKNOWN","UNCERTAIN").contains(progress.syncStatus())) {
            state="INCOMPLETE";message="现有上传记录缺少可核查的回执证据，已保留当前状态；本次不会上传、生成或创建图文。";
        }
        return new MaterialSyncVerification(material,initial.revision,state,message,progress);
    }
    public CreationState retryMaterialSync(TenantId tenant,String requestedId,String material,Version version) {
        String id=sharedId(tenant,requestedId,true);writable(tenant,WriteAction.SAVE_PLAN);
        var initial=store.get(tenant.value(),id);
        if(initial.revision!=version.revision)throw new BizException(1404,"草稿已更新，请刷新后重试");
        if("CREATED".equals(initial.publishStatus)&&find(initial,material).pendingVersion()&&publishedMaterials!=null)publishedMaterials.requireEditor(tenant,requestedId,initial);else editable(initial);
        if(!store.claim(initial))throw new BizException(1403,"素材正在处理，请稍后核查状态");
        CreationState reserved=null;
        try {
            var inspected=materialProcessing.inspect(initial,find(initial,material));
            if("UNCERTAIN".equals(inspected.progress().syncStatus()))throw new BizException(1523,"千川上传结果待确认，请先核对平台上传记录，不会重复上传");
            if(!inspected.progress().canRetrySync())throw new BizException(1503,inspected.progress().message()==null?"当前素材没有可重试的失败同步项":inspected.progress().message());
            reserved=store.mutate(tenant.value(),id,version.revision,state->{
                var m=find(state,material);
                if("CREATED".equals(state.publishStatus)&&m.pendingVersion()&&publishedMaterials!=null)publishedMaterials.requireEditor(tenant,requestedId,state);else editable(state);
                m.status="PROCESSING";m.error=null;m.failureStage=null;
                if(m.generationRequestId!=null)state.generationAdditions.stream().filter(r->r.id.equals(m.generationRequestId)&&"FAILED".equals(r.status)).findFirst().ifPresent(r->{r.status="IMPORTING";r.error=null;state.pendingAdditionId=r.id;});
                if(m.engineJobId!=null&&m.engineJobId.equals(state.engineJobId)) {
                    state.generationStatus="IMPORTING";state.generationStage="syncing";state.generationMessage="产物已保留，正在重新同步失败组件";
                }
                state.mediaChanged();
            });
            for(String asset:inspected.retryAssets())assets.retry(tenant,asset,initial.accountId,false);
            return reserved;
        }catch(BizException failure) {
            if(reserved!=null) {
                int revision=reserved.revision;
                store.mutate(tenant.value(),id,null,state->{if(state.revision==revision){var m=find(state,material);m.status="FAILED";m.failureStage="SYNC";m.error="同步重试未完成，请核查当前处理状态。已排队的组件不会重复提交。";}});
            }
            throw failure;
        }finally{store.release(initial);}
    }
    public CreationState repairTypography(TenantId tenant,String requestedId,String material,Version v) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        var s=store.get(tenant.value(),id);editable(s);
        if(s.revision!=v.revision)throw new BizException(1404,"草稿已更新，请刷新后重试");
        if(authorizationCapabilities!=null)authorizationCapabilities.requireMaterials(tenant,s.accountId);
        var original=find(s,material);
        String materialPlan=original.enginePlanId==null?s.enginePlanId:original.enginePlanId;
        if(original.engineMaterialId==null||original.engineJobId==null||original.groupId==null||materialPlan==null||!"READY".equals(original.status)
                ||!Set.of("pending","core_only","complete").contains(Objects.toString(original.typographyStatus,""))
                ||!Set.of("IMAGE","VIDEO").contains(original.type))
            throw new BizException(1503,"仅支持重排已就绪的 AI 图片或视频封面文字");
        String request=UUID.nameUUIDFromBytes((id+":"+material+":typography:"+v.revision).getBytes(StandardCharsets.UTF_8)).toString();
        var receipt=engine.post("/api/v1/capabilities/generation/materials/"+CreationEngineClient.query(original.engineMaterialId)+"/typography",
                Map.of("store_id",s.tenantId,"job_id",original.engineJobId,"ai_plan_task_id",materialPlan,
                        "group_id",original.groupId,"request_id",request));
        String mid=receipt.path("material_id").asText();
        String artwork="VIDEO".equals(original.type)?receipt.path("urls").path("cover_url").asText():receipt.path("urls").path("image_urls").path(0).asText();
        if(mid.isBlank()||mid.equals(original.engineMaterialId)||artwork.isBlank()
                ||!original.engineMaterialId.equals(receipt.path("parent_material_id").asText())
                ||!original.engineJobId.equals(receipt.path("job_id").asText())||!"succeeded".equals(receipt.path("status").asText()))
            throw new BizException(8002,"文字完善回执不匹配，原素材已保留");
        var next=json.convertValue(original,CreationState.Material.class);
        next.id=mid;next.engineMaterialId=mid;next.replacesId=original.id;next.status="PROCESSING";next.error=null;
        next.typographyStatus=receipt.path("typography_status").asText("pending");next.deliveryMode=receipt.path("delivery_mode").asText(null);
        next.sourceUrls.clear();next.importedAssets.clear();next.coverAssetId=null;
        if("VIDEO".equals(next.type)) {
            next.sourceUrls.put("COVER",artwork);
            if(next.videoAssetId!=null)next.importedAssets.put("VIDEO",next.videoAssetId);
        }else {
            next.imageAssetIds=new ArrayList<>();next.sourceUrls.put("IMAGE_0",artwork);
        }
        return store.mutate(tenant.value(),id,v.revision,state->{
            editable(state);find(state,material);next.enginePlanId=materialPlan;state.materials.add(next);state.engineJobId=original.engineJobId;state.enginePlanId=materialPlan;state.generationGroupProducts.clear();state.generationGroupProducts.put(original.groupId,original.productId);
            state.regeneration=null;state.engineTerminalStatus="succeeded";state.pendingRegenerationId=original.id;state.resetGenerationReconciliation();
            state.generationProductIds=new ArrayList<>(List.of(original.productId));state.generationStatus="IMPORTING";
            state.generationMessage="文字已处理，正在同步图片；原素材暂时保留";state.generationStage="syncing";state.progress=80;state.mediaChanged();
        });
    }
    public CreationState regenerate(TenantId tenant,String requestedId,String material,Feedback feedback) {
        String id=sharedId(tenant,requestedId,true);writable(tenant,WriteAction.SAVE_PLAN);
        if(authorizationCapabilities!=null)authorizationCapabilities.requireMaterials(tenant,store.get(tenant.value(),id).accountId);
        String instruction=com.lynx.ad.modules.delivery.domain.service.RegenerationPolicy.instruction(feedback.reasons,feedback.description);
        var reserved=store.mutate(tenant.value(),id,feedback.revision,state->{
            boolean published="CREATED".equals(state.publishStatus)&&publishedMaterials!=null;
            if(published)publishedMaterials.requireEditor(tenant,requestedId,state);else editable(state);
            var m=find(state,material);
            if(m.pendingVersion()||state.materials.stream().anyMatch(v->m.id.equals(v.reviewSourceId)&&v.pendingVersion()))throw new BizException(1503,"该素材已有待处理的新版本，请先查看新版本");
            if(m.engineMaterialId==null||m.engineJobId==null)throw new BizException(1503,"该素材为本地上传，请重新上传替换；AI 素材可重新生成");
            String materialPlan=m.enginePlanId==null?state.enginePlanId:m.enginePlanId;
            if(materialPlan==null||m.groupId==null||!Set.of("VIDEO","IMAGE","CAROUSEL").contains(Objects.toString(m.type,"")))throw new BizException(1503,"素材生成归属信息不完整，请先核查原任务");
            com.lynx.ad.modules.delivery.domain.service.RegenerationPolicy.reserve(state,System.currentTimeMillis());
            if(published)publishedMaterials.reserve(tenant,requestedId,state,m,"regenerate");
            recordFeedback(state,material,"regenerate",feedback);
            var r=new CreationState.Regeneration();r.requestId=UUID.randomUUID().toString();r.sourceId=m.id;r.parentId=m.engineMaterialId;r.jobId=m.engineJobId;r.planTaskId=materialPlan;r.groupId=m.groupId;r.target=additionTarget(m.type);r.instruction=instruction;
            state.enginePlanId=materialPlan;state.generationGroupProducts.clear();state.generationGroupProducts.put(m.groupId,m.productId);
            state.regeneration=r;state.engineJobId=m.engineJobId;state.engineTerminalStatus=null;state.engineTerminalJobId=null;state.pendingRegenerationId=m.id;
            state.generationProductIds=new ArrayList<>(List.of(m.productId));state.generationStatus="REVISING";state.generationMessage="正在提交本次素材修订，原素材已保留";state.progress=0;state.generationStage="queued";
            state.generationCounts=new ArrayList<>(List.of(new CreationState.GenerationCount(m.type,0,1)));state.generationIssues.clear();state.resetGenerationReconciliation();state.changed();
        });
        return regeneration.submit(reserved);
    }
    public CreationState resolveMaterialVersion(TenantId tenant,String requestedId,String material,VersionDecision request) {
        if(!Set.of("REPLACE","KEEP_BOTH","DISCARD").contains(Objects.toString(request.decision,"")))throw new BizException(1503,"请选择有效的新版本处理方式");
        String id=sharedId(tenant,requestedId,true);writable(tenant,WriteAction.SAVE_PLAN);
        var snapshot=store.get(tenant.value(),id);var candidate=find(snapshot,material);
        if(authorizationCapabilities!=null)authorizationCapabilities.requireMaterials(tenant,snapshot.accountId);
        if(candidate.reviewSourceId==null)throw new BizException(1503,"该素材不是待确认的新版本");
        if(request.decision.equals(candidate.versionDecision))return snapshot;
        if("DISCARD".equals(request.decision))return store.mutate(tenant.value(),id,request.revision,s->{
            var failed=find(s,material);
            if(!failed.pendingVersion()||!"FAILED".equals(failed.status)||!"PENDING".equals(failed.versionDecision))throw new BizException(1503,"仅可放弃处理失败的新版本");
            if("CREATED".equals(s.publishStatus)&&publishedMaterials!=null)publishedMaterials.requireEditor(tenant,requestedId,s);else editable(s);
            s.materials.remove(failed);s.removedMaterialIds.add(failed.id);s.changed();
        });
        if(PublishedMaterialService.pending(snapshot)&&material.equals(snapshot.materialEdit.versionId)){
            if(!("APPLYING_"+request.decision).equals(candidate.versionDecision))throw new BizException(1404,"新版本处理方式已提交，请先核查结果");
            return publishedMaterials.process(snapshot);
        }
        if(!candidate.pendingVersion()||!"READY".equals(candidate.status))throw new BizException(1503,"新版本尚未就绪或已处理，请刷新后核对");
        payloads.materialReady(snapshot,candidate);
        var result=store.mutate(tenant.value(),id,request.revision,s->{
            var next=find(s,material);var source=find(s,next.reviewSourceId);
            if(!next.pendingVersion()||!"PENDING".equals(next.versionDecision)||!"READY".equals(next.status)||!source.productId.equals(next.productId)||!source.type.equals(next.type))throw new BizException(1404,"素材版本已变化，请刷新后核对");
            if("KEEP_BOTH".equals(request.decision)&&s.activeMaterials().size()>=100)throw new BizException(1503,"每个计划最多使用100个素材，可选择替换原素材");
            if("CREATED".equals(s.publishStatus)&&publishedMaterials!=null){
                publishedMaterials.reserve(tenant,requestedId,s,source,"KEEP_BOTH".equals(request.decision)?"keep":"replace");
                s.materialEdit.versionId=next.id;var single=payloads.optimizationMaterial(s,next);
                for(var member:s.materialEdit.members)member.added=PublishedMaterialService.with(member.before,single);
                next.versionDecision="APPLYING_"+request.decision;s.materialEdit.message="正在将已确认的新版本保存到投放计划";s.changed();
            }else{
                editable(s);
                if("REPLACE".equals(request.decision)){s.materials.remove(source);s.removedMaterialIds.add(source.id);}
                next.versionDecision=request.decision;s.mediaChanged();
            }
        });
        return PublishedMaterialService.pending(result)&&publishedMaterials!=null?publishedMaterials.process(result):result;
    }
    public CreationState confirm(TenantId tenant,String requestedId,Version v) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);var s=store.get(tenant.value(),id);editable(s);
        for(var p:s.products)if(s.activeMaterials().stream().noneMatch(m->p.qcProductId().equals(m.productId)))throw new BizException(1503,"请为所有商品补齐素材");
        for(var m:s.activeMaterials())payloads.materialReady(s,m);
        return store.mutate(tenant.value(),id,v.revision,state->{editable(state);state.confirmedVersion=state.packageVersion;});
    }
    public CreationState publish(TenantId tenant,String requestedId,Publish request) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.PUBLISH);
        if(request.requestKey==null||!request.requestKey.matches("[A-Za-z0-9_-]{16,64}"))throw new BizException(1402,"发布请求标识不合法");
        var replay=store.attempt(tenant.value(),request.requestKey);
        if(replay.isPresent()) {if(!id.equals(replay.get().planId())||request.revision!=replay.get().revision())throw new BizException(1404,"发布标识已用于其他计划版本");return get(tenant,id);}
        var s=store.get(tenant.value(),id);if(s.publishedEdit!=null)throw new BizException(1403,"请使用保存修改更新原投放计划");if(s.publishKey!=null&&Set.of("QUEUED","SENDING","UNCERTAIN","CREATED").contains(s.publishStatus))return get(tenant,id);editable(s);qc.selected(tenant,s.accountId,s.awemeId,ids(s));
        if(s.revision!=request.revision||s.packageVersion!=request.packageVersion)throw new BizException(1404,"素材版本已变化，请核对后发布");
        if(authorizationCapabilities!=null)authorizationCapabilities.requireMaterials(tenant,s.accountId);
        s.confirmedVersion=s.packageVersion;
        var body=payloads.build(s);String hash=hash(body.toString());
        return store.mutate(tenant.value(),id,request.revision,state->{
            if(state.publishKey!=null&&Set.of("QUEUED","SENDING","UNCERTAIN","CREATED").contains(state.publishStatus))return;
            editable(state);if(request.packageVersion!=state.packageVersion)throw new BizException(1404,"素材版本已变化");
            state.confirmedVersion=state.packageVersion;
            store.attempt(new CreationStore.Attempt(UUID.randomUUID().toString(),state.tenantId,id,state.revision,request.requestKey,hash,body.toString(),"QUEUED",null,null));
            state.publishKey=request.requestKey;state.publishStatus="QUEUED";state.publishError=null;state.publishRecovery=null;state.step=4;
            var p=plan(state);p.apply(PlanStatus.SUBMITTING);plans.save(p);
        });
    }
    public void discard(TenantId tenant,String requestedId) {
        String id=sharedId(tenant,requestedId,true);
        var s=store.get(tenant.value(),id);if(s.publishedEdit!=null){compositions.discard(tenant,id);return;}if(!Set.of("NONE","FAILED").contains(s.publishStatus))throw new BizException(1403,"已提交发布的计划不能丢弃");
        store.mutate(tenant.value(),id,null,state->{if(!Set.of("NONE","FAILED").contains(state.publishStatus))throw new BizException(1403,"计划已提交");state.analysisStatus="CANCELLED";state.generationStatus="CANCELLED";});
        if(s.engineJobId!=null)try{engine.post("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(s.engineJobId)+"/control",Map.of("store_id",s.tenantId,"action","cancel"));}catch(BizException ignored){}
        tx.executeWithoutResult(status->{if(!plans.discardDraft(tenant,new PlanId(id)))throw new BizException(1403,"仅可丢弃未发布草稿");bindings.upsert(new PlanId(id),List.of());store.delete(tenant.value(),id);});
    }
    public void process(CreationState snapshot) {
        if(snapshot.publishedEditPending()&&compositions!=null){compositions.process(snapshot);return;}
        if(publishedMaterials!=null&&PublishedMaterialService.pending(snapshot)
                &&(!"GENERATING".equals(snapshot.materialEdit.status)||Set.of("SUCCEEDED","FAILED").contains(snapshot.generationStatus))) {
            publishedMaterials.process(snapshot);return;
        }
        if(snapshot.generationRetry!=null&&"SENDING".equals(snapshot.generationRetry.status)) {
            var receipt=engine.find("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(snapshot.engineJobId)+"/retry-failed/"+CreationEngineClient.query(snapshot.generationRetry.requestId)+"?store_id="+CreationEngineClient.query(snapshot.tenantId));
            acceptGenerationRetry(snapshot,receipt);return;
        }
        if("REVISING".equals(snapshot.generationStatus)) {
            if(snapshot.regeneration==null) {
                store.mutate(snapshot.tenantId,snapshot.planId,null,s->{if("REVISING".equals(s.generationStatus)&&s.regeneration==null){s.generationStatus="UNCERTAIN";s.generationMessage="历史重新生成缺少修订回执，请核查原任务；原素材已保留，不会重复提交";}});return;
            }
            if(!regeneration.refresh(snapshot))return;
            process(store.get(snapshot.tenantId,snapshot.planId));return;
        }
        if(snapshot.pendingAdditionId!=null) {
            var request=additionRecord(snapshot,snapshot.pendingAdditionId);
            if(Set.of("QUEUED","SUBMITTING","RUNNING","IMPORTING","UNCERTAIN").contains(request.status)){processAddition(snapshot,request);return;}
        }
        var tenant=new TenantId(snapshot.tenantId);String id=snapshot.planId;
        if(Set.of("SUBMITTING","UNCERTAIN").contains(snapshot.generationStatus)) {
            if(snapshot.pendingRegenerationId!=null){store.mutate(snapshot.tenantId,id,null,s->{if("UNCERTAIN".equals(s.generationStatus))s.generationMessage="本次重新生成尚无可核验回执，原素材已保留，不会以旧任务代替新结果";});return;}
            if(snapshot.engineJobId!=null){store.mutate(snapshot.tenantId,id,null,s->s.generationStatus="RUNNING");return;}
            if(snapshot.enginePlanId==null)return;
            var remote=engine.get("/api/v1/plan-tasks/"+CreationEngineClient.query(snapshot.enginePlanId));
            if(!snapshot.tenantId.equals(remote.path("store_id").asText()))throw new BizException(8002,"引擎任务归属不匹配");
            String job=remote.path("context").path("current_generation_job_id").asText();
            if(!job.isBlank()&&(snapshot.engineJobId!=null||!job.equals(snapshot.previousEngineJobId)))
                store.mutate(snapshot.tenantId,id,null,s->{s.engineJobId=job;s.generationStatus="RUNNING";s.generationMessage="已恢复引擎任务，继续查询结果";});
            else store.mutate(snapshot.tenantId,id,null,s->{s.generationStatus="UNCERTAIN";s.generationMessage="引擎任务提交结果待确认，不会自动重复生成";});
            return;
        }
        // Analysis is advisory. Explicit generation and material imports take priority.
        if(Set.of("QUEUED","RUNNING").contains(snapshot.analysisStatus)
                &&Set.of("NONE","FAILED","SUCCEEDED","CANCELLED").contains(snapshot.generationStatus)
                &&snapshot.materials.stream().noneMatch(m->"PROCESSING".equals(m.status))) {
            processAnalysis(snapshot);
            return;
        }
        if("QUEUED".equals(snapshot.generationStatus)) {
            if(snapshot.enginePlanId==null) {
                CreationState registered;
                try { registered=registerEnginePlan(snapshot); }
                catch(BizException unavailable) {
                    store.mutate(snapshot.tenantId,id,null,s->{if("QUEUED".equals(s.generationStatus)){s.generationStatus="FAILED";s.generationMessage="引擎任务初始化失败，尚未提交素材生成，可稍后重试";s.generationIssues=new ArrayList<>(GenerationIssueMapper.requested(s,s.generationMessage));}});
                    return;
                }
                process(registered);
                return;
            }
            store.mutate(snapshot.tenantId,id,null,s->{s.generationStatus="SUBMITTING";s.generationMessage="正在提交引擎任务";});
            try {
                var groupBody=new LinkedHashMap<String,Object>();groupBody.put("ai_plan_task_id",snapshot.enginePlanId);groupBody.put("store_id",snapshot.tenantId);groupBody.put("products",engineProducts(snapshot));groupBody.put("budget",snapshot.budget);if(snapshot.roi2Goal!=null)groupBody.put("roi",snapshot.roi2Goal);
                var groups=engine.post("/api/v1/capabilities/perception/products/recommend-groups",groupBody);
                var selected=new ArrayList<Map<String,Object>>();
                for(int i=0;i<snapshot.products.size();i++)if(snapshot.generationProductIds.contains(snapshot.products.get(i).qcProductId())) {
                    var product=snapshot.products.get(i);if(product.imageUrl()==null||product.imageUrl().isBlank())throw new BizException(1503,"商品主图缺失，无法生成素材："+product.name());
                    var p=engineProduct(product);p.put("image_urls",List.of(product.imageUrl()));selected.add(Map.of("group_id","group-"+(i+1),"products",List.of(p)));
                }
                var body=new LinkedHashMap<String,Object>(groupBody);body.put("product_groups",selected);body.put("platform","douyin");body.put("constraints",Map.of("source","lynxad-app"));
                var job=engine.post("/api/v1/capabilities/generation/jobs",body);String jobId=job.path("job_id").asText(job.path("id").asText());
                if(jobId.isBlank())throw new BizException(8003,"引擎未返回生成任务 ID，提交结果待确认");
                store.mutate(snapshot.tenantId,id,null,s->{if("SUBMITTING".equals(s.generationStatus)){s.engineJobId=jobId;s.groups=groups;s.generationStatus="RUNNING";s.generationMessage="AI 引擎生成中";}});
            }catch(BizException e){store.mutate(snapshot.tenantId,id,null,s->{if("SUBMITTING".equals(s.generationStatus)){s.generationStatus=e.code()==8003?"UNCERTAIN":"FAILED";s.generationMessage=e.getMessage();if("FAILED".equals(s.generationStatus))s.generationIssues=new ArrayList<>(GenerationIssueMapper.requested(s,s.generationMessage));}});}
            return;
        }
        var current=store.get(snapshot.tenantId,id);
        boolean recoveringSummary="FAILED".equals(current.generationStatus)&&(current.needsGenerationReconciliation()||current.materials.stream().anyMatch(m->"PROCESSING".equals(m.status)));
        if(current.engineJobId!=null && (Set.of("RUNNING","IMPORTING").contains(current.generationStatus)||recoveringSummary)) {
            String polledJobId=current.engineJobId;
            if(current.needsGenerationReconciliation())store.mutate(current.tenantId,id,null,s->{
                if(Objects.equals(s.engineJobId,polledJobId)&&s.needsGenerationReconciliation()) {
                    s.generationReconciliationAttempts++;s.generationReconciledAt=System.currentTimeMillis();
                }
            });
            var job=engine.get("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(polledJobId)+"?store_id="+CreationEngineClient.query(current.tenantId));
            if(!polledJobId.equals(job.path("job_id").asText()))throw new BizException(8002,"生成任务响应不匹配");
            if(!CreationRegeneration.outputMatches(current,job)) {
                String request=current.regeneration.requestId;
                store.mutate(current.tenantId,id,null,s->{if(s.regeneration!=null&&request.equals(s.regeneration.requestId))s.generationMessage="正在等待本次修订的新素材记录，原素材已保留，不采用旧任务结果";});return;
            }
            var ready=new ArrayList<CreationState.Material>();
            for(var group:job.path("groups")) {
                String groupId=group.path("group_id").asText();int index;
                try{index=Integer.parseInt(groupId.substring("group-".length()))-1;}catch(Exception ignored){continue;}
                String productId=current.generationGroupProducts.isEmpty()?(index>=0&&index<current.products.size()?current.products.get(index).qcProductId():null):current.generationGroupProducts.get(groupId);
                if(productId==null||!ids(current).contains(productId))continue;
                for(var item:group.path("materials")) {
                    if(!"v1".equals(item.path("version").asText())||!"succeeded".equals(item.path("status").asText()))continue;
                    String mid=item.path("material_id").asText();if(mid.isBlank()||current.removedMaterialIds.contains(mid)||current.materials.stream().anyMatch(m->m.id.equals(mid)))continue;
                    var m=new CreationState.Material();m.id=mid;m.engineMaterialId=mid;m.engineJobId=current.engineJobId;m.enginePlanId=current.enginePlanId;m.groupId=groupId;m.productId=productId;
                    m.type=switch(item.path("material_type").asText()){case "video"->"VIDEO";case "image_text"->"CAROUSEL";case "product_card"->"IMAGE";default->"";};if(m.type.isEmpty())continue;
                    m.title=item.path("content").path("title").asText();m.text=item.path("content").path("text").asText();
                    m.deliverySource=item.path("detail").path("delivery_source").asText(null);
                    m.deliveryMode=item.path("detail").path("delivery_mode").asText(null);
                    m.typographyStatus=item.path("detail").path("typography_status").asText(null);
                    m.deliveryFamilyId=item.path("detail").path("delivery_family_id").asText(null);
                    m.titles=new ArrayList<>(List.of(m.title));var urls=item.path("urls");
                    if("VIDEO".equals(m.type)){m.sourceUrls.put("VIDEO",urls.path("video_url").asText());m.sourceUrls.put("COVER",urls.path("cover_url").asText());}
                    else {int n=0;for(var url:urls.path("image_urls"))m.sourceUrls.put("IMAGE_"+n++,url.asText());}
                    if("CAROUSEL".equals(m.type))m.sourceUrls.put("AUDIO",urls.path("voice_url").asText());
                    String parent=item.path("detail").path("parent_material_id").asText(null);m.replacesId=parent;
                    if(current.regeneration!=null&&mid.equals(current.regeneration.resultMaterialId)&&Objects.equals(parent,current.regeneration.parentId)){m.replacesId=null;m.reviewSourceId=current.regeneration.sourceId;m.versionDecision="PENDING";}
                    ready.add(m);
                }
            }
            String status=job.path("status").asText();
            store.mutate(current.tenantId,id,null,s->{
                if(!Objects.equals(s.engineJobId,polledJobId)||!Set.of("RUNNING","IMPORTING","FAILED").contains(s.generationStatus))return;
                for(var m:ready)if(!s.removedMaterialIds.contains(m.id)&&s.materials.stream().noneMatch(x->x.id.equals(m.id))&&(m.pendingVersion()?s.materials.size()<200:s.activeMaterials().size()<100)){s.materials.add(m);if(m.pendingVersion())s.changed();else s.mediaChanged();}
                s.progress=engineProgress(s,job);s.generationMessage=job.path("progress").path("message").asText("正在登记引擎素材并同步千川");
                updateGenerationSummary(s,job);
                s.generationStage=job.path("progress").path("stage").asText("generation");
                if(Set.of("succeeded","partial_failed","failed","cancelled").contains(status)){s.engineTerminalStatus=status;s.engineTerminalJobId=polledJobId;s.generationStatus="IMPORTING";s.generationStage="syncing";s.progress=Math.max(s.progress,80);if(generationNotices!=null)generationNotices.generated(s);}
                else {s.engineTerminalStatus=null;s.engineTerminalJobId=null;s.generationStatus="RUNNING";s.resetGenerationReconciliation();}
            });
        }
        current=store.get(snapshot.tenantId,id);
        for(var material:current.materials)if(!"READY".equals(material.status)&&!"FAILED".equals(material.status))processMaterial(current,material);
        store.mutate(snapshot.tenantId,id,null,s->{
            s.retireReferenceAlternatives();
            if(!Set.of("IMPORTING","FAILED").contains(s.generationStatus)||s.engineTerminalStatus==null)return;
            long finished=s.materials.stream().filter(m->Set.of("READY","FAILED").contains(m.status)).count();
            if(finished<s.materials.size()){s.progress=Math.max(s.progress,80+(int)(19*finished/s.materials.size()));return;}
            boolean all=s.generationProductIds.stream().allMatch(p->s.materials.stream().anyMatch(m->m.productId.equals(p)&&"READY".equals(m.status)));
            boolean replacement=s.pendingRegenerationId==null||s.materials.stream().anyMatch(m->(s.pendingRegenerationId.equals(m.replacesId)||s.pendingRegenerationId.equals(m.reviewSourceId))&&"READY".equals(m.status));
            boolean completeCounts=s.generationCounts.stream().allMatch(c->c.total()==null||c.completed()>=c.total());
            boolean succeeded=all&&replacement&&completeCounts&&!s.materials.isEmpty()&&s.materials.stream().allMatch(m->"READY".equals(m.status))&&"succeeded".equals(s.engineTerminalStatus);
            s.generationStatus=succeeded?"SUCCEEDED":"FAILED";s.progress=succeeded?100:Math.max(s.progress,80);
            s.generationMessage=succeeded?"素材已就绪并同步千川":s.materials.stream().anyMatch(m->"FAILED".equals(m.status))?
                "部分素材同步未完成，可继续使用已就绪素材":"部分 AI 创意未完成，已有可用素材已保留，可从下方选用";
            s.generationStage="finished";
        });
    }
    private void processAddition(CreationState snapshot,CreationState.GenerationAddition request) {
        String tenant=snapshot.tenantId,id=snapshot.planId,rid=request.id;
        if(snapshot.enginePlanId==null) {
            CreationState registered;
            try {
                var taskState=registerEnginePlan(snapshot);String task=taskState.enginePlanId;
                registered=store.mutate(tenant,id,null,s->{
                    var r=additionRecord(s,rid);
                    ((com.fasterxml.jackson.databind.node.ObjectNode)r.engineRequest).put("ai_plan_task_id",task);
                });
            }catch(BizException unavailable){failAddition(tenant,id,rid,"引擎任务初始化失败，尚未提交新增素材生成，可稍后重试");return;}
            processAddition(registered,additionRecord(registered,rid));
            return;
        }
        if(request.engineJobId==null) {
            boolean posted=false;
            try {
                var receipt=engine.find("/api/v1/capabilities/generation/materials/additions/"+CreationEngineClient.query(rid)+"?store_id="+CreationEngineClient.query(tenant));
                if(receipt==null){posted=true;receipt=engine.post("/api/v1/capabilities/generation/materials/additions",request.engineRequest);}
                if(!rid.equals(receipt.path("request_id").asText())||!request.productId.equals(receipt.path("product_id").asText())||!additionTarget(request.type).equals(receipt.path("target").asText())
                        ||!snapshot.enginePlanId.equals(receipt.path("ai_plan_task_id").asText())||receipt.path("job_id").asText().isBlank()){
                    failAddition(tenant,id,rid,"引擎新增素材回执不匹配，请核查原任务");return;
                }
                String jobId=receipt.path("job_id").asText();
                store.mutate(tenant,id,null,s->{var r=additionRecord(s,rid);r.engineJobId=jobId;r.status="RUNNING";r.error=null;s.engineJobId=jobId;s.generationStatus="RUNNING";s.generationMessage="正在生成新增素材";s.generationStage="generation";});
            }catch(BizException ex){
                if(posted&&ex.code()==8002){failAddition(tenant,id,rid,ex.getMessage());return;}
                store.mutate(tenant,id,null,s->{var r=additionRecord(s,rid);r.status="UNCERTAIN";r.error="提交状态待确认，正在查询原请求";s.generationStatus="UNCERTAIN";s.generationMessage=r.error;});
            }
            return;
        }
        // Receipt reads also recover an accepted inline task after an engine restart.
        try{engine.find("/api/v1/capabilities/generation/materials/additions/"+CreationEngineClient.query(rid)+"?store_id="+CreationEngineClient.query(tenant));}catch(BizException unavailable){/* Keep reading an already registered job when dispatch is paused. */}
        var job=engine.get("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(request.engineJobId)+"?store_id="+CreationEngineClient.query(tenant));
        if(!request.engineJobId.equals(job.path("job_id").asText())){failAddition(tenant,id,rid,"新增素材生成任务不匹配");return;}
        var outputs=new ArrayList<CreationState.Material>();
        for(var group:job.path("groups"))for(var item:group.path("materials")) {
            if(!"v1".equals(item.path("version").asText())||!"succeeded".equals(item.path("status").asText()))continue;
            if(!additionTarget(request.type).equals(item.path("material_type").asText())){failAddition(tenant,id,rid,"引擎返回了未请求的素材类型");return;}
            String product=item.path("content").path("target_product_id").asText(request.productId);
            if(!request.productId.equals(product)){failAddition(tenant,id,rid,"新增素材关联了其他商品");return;}
            String mid=item.path("material_id").asText();if(mid.isBlank())continue;
            var m=new CreationState.Material();m.id=mid;m.engineMaterialId=mid;m.engineJobId=request.engineJobId;m.enginePlanId=snapshot.enginePlanId;m.groupId=group.path("group_id").asText();m.productId=request.productId;m.type=request.type;
            m.deliverySource=item.path("detail").path("delivery_source").asText(null);
            m.deliveryMode=item.path("detail").path("delivery_mode").asText(null);
            m.deliveryFamilyId=item.path("detail").path("delivery_family_id").asText(null);
            m.typographyStatus=item.path("detail").path("typography_status").asText(null);
            m.generationRequestId=rid;m.generationDescription=request.description;m.title=item.path("content").path("title").asText();m.text=item.path("content").path("text").asText();m.titles=new ArrayList<>(List.of(m.title));
            var urls=item.path("urls");if("VIDEO".equals(m.type)){m.sourceUrls.put("VIDEO",urls.path("video_url").asText());m.sourceUrls.put("COVER",urls.path("cover_url").asText());}
            else{int n=0;for(var url:urls.path("image_urls"))m.sourceUrls.put("IMAGE_"+n++,url.asText());}
            if("CAROUSEL".equals(m.type))m.sourceUrls.put("AUDIO",urls.path("voice_url").asText());
            outputs.add(m);
        }
        if(outputs.size()>1){failAddition(tenant,id,rid,"单条新增任务返回多条素材，已停止导入");return;}
        boolean terminal=Set.of("succeeded","partial_failed","failed","cancelled").contains(job.path("status").asText());
        store.mutate(tenant,id,null,s->{
            var r=additionRecord(s,rid);
            if(!outputs.isEmpty()) {
                var m=outputs.getFirst();r.materialId=m.id;
                if(s.materials.stream().noneMatch(old->old.id.equals(m.id))&&!s.removedMaterialIds.contains(m.id)){
                    if(s.activeMaterials().size()>=100)throw new BizException(1503,"素材数量已达上限");s.materials.add(m);s.mediaChanged();
                }
                r.status="IMPORTING";s.generationStatus="IMPORTING";s.generationStage="syncing";
                s.generationCounts=new ArrayList<>(List.of(new CreationState.GenerationCount(r.type,1,1)));
            }
            s.progress=outputs.isEmpty()?engineProgress(s,job):Math.max(s.progress,80);
            s.generationMessage=outputs.isEmpty()?job.path("progress").path("message").asText("新增素材生成中"):"新增素材已生成，正在同步千川";
        });
        if(terminal)store.mutate(tenant,id,null,s->{if(Objects.equals(s.engineJobId,request.engineJobId)){s.engineTerminalStatus=job.path("status").asText();s.engineTerminalJobId=request.engineJobId;}});
        var current=store.get(tenant,id);String currentMaterialId=additionRecord(current,rid).materialId;
        var material=current.materials.stream().filter(m->Objects.equals(m.id,currentMaterialId)).findFirst().orElse(null);
        if(material!=null&&!Set.of("READY","FAILED").contains(material.status))processMaterial(current,material);
        current=store.get(tenant,id);String savedMaterialId=additionRecord(current,rid).materialId;
        final var saved=current.materials.stream().filter(m->Objects.equals(m.id,savedMaterialId)).findFirst().orElse(null);
        boolean ready=saved!=null&&"READY".equals(saved.status);
        if(terminal&&(saved==null||Set.of("READY","FAILED").contains(saved.status))) {
            if(ready&&"succeeded".equals(job.path("status").asText()))store.mutate(tenant,id,null,s->{var done=additionRecord(s,rid);done.status="SUCCEEDED";done.error=null;s.generationStatus="SUCCEEDED";s.generationMessage="新增素材已生成并同步千川";s.generationStage="finished";s.progress=100;s.generationCounts=new ArrayList<>(List.of(new CreationState.GenerationCount(done.type,1,1)));});
            else failAddition(tenant,id,rid,saved!=null&&saved.error!=null?saved.error:GenerationIssueMapper.message(job.path("error"),"本次新增素材未完成，原有素材已保留"));
        }
    }
    private void failAddition(String tenant,String id,String rid,String error) {
        store.mutate(tenant,id,null,s->{var r=additionRecord(s,rid);r.status="FAILED";r.error=error;s.generationStatus="FAILED";s.generationMessage=error;s.generationStage="finished";
            var product=s.products.stream().filter(p->p.qcProductId().equals(r.productId)).findFirst().orElse(null);s.generationIssues=new ArrayList<>(List.of(new CreationState.GenerationIssue(r.productId,product==null?r.productId:product.name(),error,r.type,r.materialId,"新增"+switch(r.type){case "VIDEO"->"视频";case "IMAGE"->"商品卡图片";default->"图文";},"addition:"+rid)));});
    }
    private static int engineProgress(CreationState state,JsonNode job) {
        return Math.max(state.progress,Math.max(0,Math.min(80,job.path("progress").path("percent").asInt(state.progress))));
    }
    private static void updateGenerationSummary(CreationState s,JsonNode job) {
        s.generationCounts.clear();s.generationIssues.clear();
        var types=Map.of("VIDEO","video","IMAGE","product_card","CAROUSEL","image_text");
        var expectedKeys=Map.of("VIDEO","videos","IMAGE","product_cards","CAROUSEL","image_texts");
        boolean known=job.path("groups").isArray()&&!job.path("groups").isEmpty();
        for(var group:job.path("groups")) {
            if(!selectedGenerationGroup(s,group))continue;
            var expected=group.path("expected");
            // Planning failures can have no counts. Do not invent totals for those products.
            if(!expected.isObject()||expected.path("videos").asInt()+expected.path("product_cards").asInt()+expected.path("image_texts").asInt()==0)known=false;
        }
        var issues=GenerationIssueMapper.from(s,job);
        s.generationIssues.addAll(issues.stream().filter(i->!s.generationIssueDismissed(i)).toList());
        for(String type:List.of("VIDEO","IMAGE","CAROUSEL")) {
            int completed=0,total=0;
            var families=new HashSet<String>();
            for(var group:job.path("groups")) {
            if(!selectedGenerationGroup(s,group))continue;
                total+=group.path("expected").path(expectedKeys.get(type)).asInt(0);
                for(var m:group.path("materials"))if("v1".equals(m.path("version").asText())&&"succeeded".equals(m.path("status").asText())&&types.get(type).equals(m.path("material_type").asText())) {
                    String family=m.path("detail").path("delivery_family_id").asText(m.path("material_id").asText());
                    if(families.add(family))completed++;
                }
            }
            boolean terminal=Set.of("failed","partial_failed").contains(job.path("status").asText());
            total=Math.max(completed,total-(int)issues.stream().filter(i->type.equals(i.type())&&s.generationIssueDismissed(i)).count());
            int failed=known&&terminal?Math.max(0,total-completed):terminal?(int)s.generationIssues.stream().filter(i->type.equals(i.type())).count():0;
            s.generationCounts.add(new CreationState.GenerationCount(type,completed,known?Math.max(total,completed):null,failed));
        }
    }
    private static boolean selectedGenerationGroup(CreationState s,JsonNode group){
        if(s.generationGroupProducts.isEmpty())return true;
        String id=s.generationGroupProducts.get(group.path("group_id").asText());
        final String product=id;return product!=null&&s.products.stream().anyMatch(p->product.equals(p.qcProductId()));
    }
    private void processMaterial(CreationState s,CreationState.Material m) {
        processMaterial(s,m,false);
    }
    /** Same proven import/sync path, with an independently persisted automatic job context. */
    public CreationState.Material importOptimizationMaterial(CreationState s,CreationState.Material m){processMaterial(s,m,true);return m;}
    private void processMaterial(CreationState s,CreationState.Material m,boolean automatic) {
        var tenant=new TenantId(s.tenantId);
        m.failureStage="REGISTRATION";m.failureCode=null;
        try {
            for(var entry:m.sourceUrls.entrySet())if(!m.importedAssets.containsKey(entry.getKey())) {
                if(entry.getValue().isBlank())throw new BizException(1503,"生成结果缺少必要素材组件");
                String kind=entry.getKey().equals("COVER")||entry.getKey().startsWith("IMAGE_")?"IMAGE":entry.getKey();
                String asset=importer.reference(tenant,new UserId(s.userId),s.planId+":"+m.id+":"+entry.getKey(),m.engineJobId,m.engineMaterialId,entry.getKey(),kind,"CAROUSEL".equals(m.type)?null:s.accountId,"CAROUSEL".equals(m.type));
                m.importedAssets.put(entry.getKey(),asset);
                if(!automatic)store.mutate(s.tenantId,s.planId,null,state->find(state,m.id).importedAssets.put(entry.getKey(),asset));
            }
            if(m.textAssetId==null) {
                m.textAssetId=importer.reference(tenant,new UserId(s.userId),s.planId+":"+m.id+":TEXT",m.engineJobId,m.engineMaterialId,"TEXT","TEXT",null,"CAROUSEL".equals(m.type));
            }
            m.videoAssetId=m.importedAssets.getOrDefault("VIDEO",m.videoAssetId);m.coverAssetId=m.importedAssets.getOrDefault("COVER",m.coverAssetId);m.audioAssetId=m.importedAssets.getOrDefault("AUDIO",m.audioAssetId);
            if(m.videoAssetId!=null&&m.coverAssetId!=null)assets.attachPreview(tenant,m.videoAssetId,m.coverAssetId);
            if(!m.importedAssets.isEmpty())m.imageAssetIds=m.importedAssets.entrySet().stream().filter(e->e.getKey().startsWith("IMAGE_")).map(Map.Entry::getValue).toList();
            if("CAROUSEL".equals(m.type)&&m.carouselId==null) {
                m.carouselId=carousels.create(tenant,new UserId(s.userId),carouselRequest(s,m)).id();
            }
            var mediaId="VIDEO".equals(m.type)?m.videoAssetId:!m.imageAssetIds.isEmpty()?m.imageAssetIds.getFirst():null;
            if(mediaId!=null){var a=assets.detail(tenant,mediaId);m.imageMode="VIDEO".equals(m.type)?a.width()!=null&&a.height()!=null&&a.width()>a.height()?"VIDEO_LARGE":"VIDEO_VERTICAL":"SQUARE";}
            m.failureStage="VALIDATION";
            try{payloads.materialReady(s,m);m.status="READY";m.error=null;m.failureStage=null;}
            catch(BizException e){m.status="PROCESSING";m.error=e.getMessage();m.failureCode=e.code();
                if(e.code()==1520)m.status="FAILED";
                var refs=new ArrayList<>(m.imageAssetIds);if(m.videoAssetId!=null)refs.add(m.videoAssetId);if(m.coverAssetId!=null)refs.add(m.coverAssetId);if(m.audioAssetId!=null)refs.add(m.audioAssetId);if(m.textAssetId!=null)refs.add(m.textAssetId);
                for(String ref:refs){var a=assets.detail(tenant,ref);if(a.aiReview()!=null&&Set.of("REJECTED","MANUAL_REVIEW","FAILED").contains(a.aiReview().status())){m.status="FAILED";m.failureStage="REVIEW";m.error=a.aiReview().summary();}
                    var failedSync=a.syncs().stream().filter(x->s.accountId.equals(x.accountId())&&Set.of("FAILED","UNCERTAIN","PAUSED").contains(x.status())).findFirst();
                    if(failedSync.isPresent()){m.status="FAILED";m.failureStage="SYNC";m.error=failedSync.get().errorMessage()==null?"千川同步未完成，请在素材中心查看并重试":failedSync.get().errorMessage();}}
                if(Set.of(1523,1406).contains(e.code())){m.status="FAILED";m.failureStage="SYNC";m.error=e.getMessage();}
            }
        }catch(BizException e){m.status="FAILED";m.error=e.getMessage();m.failureCode=e.code();}
        if("READY".equals(m.status)&&creativeContexts!=null)try{creativeContexts.record(s,m);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Material creative context registration pending: {}",m.id);}
        if(!automatic)store.mutate(s.tenantId,s.planId,null,state->{
            // A title edit or another user change may occur while an asset is being inspected.
            if (state.revision != s.revision) return;
            int i=-1;for(int n=0;n<state.materials.size();n++)if(state.materials.get(n).id.equals(m.id))i=n;
            if(i<0)return;state.materials.set(i,m);
            if("READY".equals(m.status)&&m.replacesId!=null&&!"CREATED".equals(state.publishStatus)){state.materials.removeIf(old->old.id.equals(m.replacesId));state.removedMaterialIds.add(m.replacesId);}
        });
    }
    private CarouselService.Create carouselRequest(CreationState s,CreationState.Material m) {
        String name=m.title.isBlank()?"AI 图文":m.title.substring(0,Math.min(100,m.title.length()));
        List<String> images=List.copyOf(m.imageAssetIds);
        // One engine material can receive newer OSS files under the same ID.
        // Retries of identical components reuse a bundle; changed content gets
        // a new request without weakening CarouselService's conflict check.
        try {
            byte[] identity=json.writeValueAsBytes(new Object[]{"creation-carousel-v2",s.planId,m.id,
                    name,m.textAssetId,images,m.audioAssetId,s.accountId});
            String key=UUID.nameUUIDFromBytes(identity).toString();
            return new CarouselService.Create(key,name,m.textAssetId,images,m.audioAssetId,List.of(s.accountId));
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot identify carousel content",e);}
    }
    public void send(CreationStore.Attempt a) {
        if(!store.claimPublish(a.tenant(),a.key()))return;
        var s=store.get(a.tenant(),a.planId());
        store.mutate(a.tenant(),a.planId(),null,state->state.publishStatus="SENDING");
        String qcId;
        try {
            var body=json.readTree(a.payload());qcId=qc.create(new TenantId(a.tenant()),s.accountId,s.awemeId,body);
        }catch(BizException e){
            failed(a,e.code()==1523?"UNCERTAIN":"FAILED",e.getMessage(),e.code());
            return;
        }
        catch(Exception e){failed(a,"UNCERTAIN","平台创建结果待确认，请核对平台计划，避免重复创建");return;}
        // Never downgrade a durable receipt when the following local transaction fails.
        store.finishPublish(a.tenant(),a.key(),"CREATED",qcId,null);
        accepted(a,qcId);
    }
    public void accepted(CreationStore.Attempt a,String qcId) {
        store.mutate(a.tenant(),a.planId(),null,s->{s.publishStatus="CREATED";s.qcPlanId=qcId;s.publishError=null;s.publishRecovery=null;
            var p=plan(s);if(p.qcPlanId().isEmpty()){p.apply(PlanStatus.SUBMITTING);p.acceptExternalId(new QcPlanId(qcId));plans.save(p);}
        });
        store.applied(a);
    }
    public void failed(CreationStore.Attempt a,String state,String message) {
        failed(a,state,message,null);
    }
    private void failed(CreationStore.Attempt a,String state,String message,Integer code) {
        var existing=store.attempt(a.tenant(),a.key());if(existing.isPresent()&&"CREATED".equals(existing.get().status())){accepted(existing.get(),existing.get().qcId());return;}
        store.finishPublish(a.tenant(),a.key(),state,null,message);
        store.mutate(a.tenant(),a.planId(),null,s->{s.publishStatus=state;s.publishError=message;
            s.publishRecovery="FAILED".equals(state)?PublishFailureRecovery.classify(code,message):null;
            if("REFRESH_ESTIMATE".equals(s.publishRecovery))s.publishError="平台效果预估已更新，请刷新预估并确认后重新发布。预算和素材已保留。";
            var p=plan(s);if("FAILED".equals(state)){p.apply(PlanStatus.DRAFT);s.step=3;s.changed();}plans.save(p);});
    }
    private void writable(TenantId tenant,WriteAction action){billing.assertWritable(tenant,Instant.now(),action);}
    private AdPlan plan(CreationState s){return plans.findById(new TenantId(s.tenantId),new PlanId(s.planId)).orElseThrow(()->new BizException(1403,"计划不存在"));}
    private void bindProducts(AdPlan p){bindings.upsert(p.id(),p.productIds().stream().map(id->new PlanCreativeBinding(null,id,null,null,List.of())).toList());}
    private static List<String> ids(CreationState s){return s.products.stream().map(CreationState.Product::qcProductId).toList();}
    private void requireKind(TenantId t,String id,String kind){if(id==null||!kind.equals(assets.detail(t,id).kind()))throw new BizException(1503,"素材类型不匹配");}
    private static CreationState.Material find(CreationState s,String id){return s.materials.stream().filter(m->m.id.equals(id)).findFirst().orElseThrow(()->new BizException(1503,"素材不属于该计划"));}
    private static void editable(CreationState s){
        appendable(s);
        if(Set.of("QUEUED","SUBMITTING","REVISING","RUNNING","IMPORTING","UNCERTAIN").contains(s.generationStatus))throw new BizException(1403,"任务正在执行，请等待完成后修改");
    }
    private static void appendable(CreationState s){
        if(s.titleGenerationPending())throw new BizException(1403,"标题正在生成，请等待本次结果后修改");
        if(s.publishedEdit!=null&&!"DRAFT".equals(s.publishedEdit.status))throw new BizException(1403,"保存结果待核查或已完成，请先核查原计划");
        if(!Set.of("NONE","FAILED").contains(s.publishStatus))throw new BizException(1403,"计划正在发布或已发布，不可修改草稿");
    }
    private static void selectionEditable(CreationState s){
        com.lynx.ad.modules.delivery.domain.service.CreationProductSelection.assertEditable(s);
        editable(s);
    }
    private static void requireBasicAnalysis(CreationState s) {
        s.basicAnalysis=ProductBasicAnalysisService.analyze(s.products);
        if(!s.basicAnalysis.canContinue())throw new BizException(1403,s.basicAnalysis.issues().stream()
                .filter(i->"ERROR".equals(i.severity())).findFirst().orElseThrow().message());
    }

    /** Register only when an engine operation is needed; goal editing never contacts the engine. */
    private CreationState registerEnginePlan(CreationState snapshot) {
        if(snapshot.enginePlanId!=null)return snapshot;
        String fingerprint=ProductBasicAnalysisService.fingerprint(snapshot.products);
        var response=engine.post("/api/v1/plan-tasks",Map.of("platform","douyin","store_id",snapshot.tenantId,
                "account",Map.of("account_id",snapshot.accountId,"aweme_id",snapshot.awemeId),
                "operator",Map.of("user_id",snapshot.userId),"metadata",Map.of("lingtou_plan_id",snapshot.planId)));
        if(response==null||!response.isObject())throw new BizException(8002,"引擎未返回有效任务信息");
        String registered=response.path("ai_plan_task_id").asText(response.path("id").asText());
        if(registered.isBlank())throw new BizException(8002,"引擎未返回计划任务 ID");
        var current=store.mutate(snapshot.tenantId,snapshot.planId,null,s->{
            if(fingerprint.equals(ProductBasicAnalysisService.fingerprint(s.products))&&Set.of("NONE","FAILED").contains(s.publishStatus)
                    &&s.enginePlanId==null)s.enginePlanId=registered;
        });
        if(!fingerprint.equals(ProductBasicAnalysisService.fingerprint(current.products))||current.enginePlanId==null)
            throw new BizException(1404,"商品或草稿状态已变化，已忽略旧引擎任务");
        return current;
    }

    private void processAnalysis(CreationState snapshot) {
        String fingerprint=ProductBasicAnalysisService.fingerprint(snapshot.products);
        String originalRequest=snapshot.analysisRequestId;
        String request=originalRequest==null?UUID.randomUUID().toString():originalRequest;
        var running=store.mutate(snapshot.tenantId,snapshot.planId,null,s->{
            if(Objects.equals(originalRequest,s.analysisRequestId)&&fingerprint.equals(ProductBasicAnalysisService.fingerprint(s.products))
                    &&Set.of("QUEUED","RUNNING").contains(s.analysisStatus)) {
                s.analysisRequestId=request;s.analysisStatus="RUNNING";
                if(s.analysisStartedAt==0)s.analysisStartedAt=System.currentTimeMillis();
            }
        });
        if(!currentAnalysis(running,request,fingerprint))return;
        try {
            var registered=registerEnginePlan(running);
            if(!currentAnalysis(registered,request,fingerprint))return;
            var response=engine.post("/api/v1/capabilities/perception/products/analyze",Map.of(
                    "ai_plan_task_id",registered.enginePlanId,"store_id",registered.tenantId,
                    "products",engineProducts(registered),"refresh",registered.analysisRefreshRequested,"analysis_mode","preparation_v1",
                    "account_id",registered.accountId,"analysis_id",request,"input_version",fingerprint));
            if(response==null||!response.isObject()||!response.has("product_set"))throw new BizException(8002,"引擎未返回有效商品分析");
            var report=response.path("selection_analysis");
            if(!report.isMissingNode() && (!fingerprint.equals(report.path("input_version").asText()) || !request.equals(report.path("analysis_id").asText())))
                throw new BizException(8002,"商品分析输入版本不一致，请重新分析");
            store.mutate(snapshot.tenantId,snapshot.planId,null,s->{
                if(currentAnalysis(s,request,fingerprint)){s.analysis=response;s.analysisConfirmation=null;
                    s.analysisStatus=ProductAnalysisOutcome.failed(response)?"FAILED":"SUCCEEDED";
                    s.analysisError=ProductAnalysisOutcome.failed(response)?ProductAnalysisOutcome.failureMessage(response):null;
                    s.analysisRefreshRequested=false;}
            });
        }catch(BizException e){
            store.mutate(snapshot.tenantId,snapshot.planId,null,s->{
                if(currentAnalysis(s,request,fingerprint)){s.analysisStatus="FAILED";s.analysisError=e.getMessage();}
            });
        }
    }
    private static boolean currentAnalysis(CreationState s,String request,String fingerprint) {
        return request.equals(s.analysisRequestId)&&"RUNNING".equals(s.analysisStatus)
                &&fingerprint.equals(ProductBasicAnalysisService.fingerprint(s.products));
    }
    private void confirmAnalysisSnapshot(CreationState s) {
        if(!"SUCCEEDED".equals(s.analysisStatus)||s.analysis==null||ProductAnalysisOutcome.failed(s.analysis)||!s.analysis.has("selection_analysis"))return;
        var report=s.analysis.path("selection_analysis");
        if(!ProductBasicAnalysisService.fingerprint(s.products).equals(report.path("input_version").asText()))return;
        String actor=TenantContext.tenantId().filter(t->t.value().equals(s.tenantId)).map(t->TenantContext.userId().value()).orElse(s.userId);
        s.analysisConfirmation=json.createObjectNode().put("analysis_id",report.path("analysis_id").asText())
                .put("input_version",report.path("input_version").asText()).put("confirmed_at",Instant.now().toString())
                .put("user_id",actor).put("selection_confirmed",true).put("combination_decision",s.groupingDecision==null?"CURRENT_SELECTION":s.groupingDecision)
                .set("handoff",report.path("handoff").deepCopy());
    }
    /** Explicitly associate an uncertain write with a verified platform plan; this performs no create. */
    public CreationState reconcile(TenantId tenant,String requestedId,Reconcile req) {
        String id=sharedId(tenant,requestedId,true);
        var s=store.get(tenant.value(),id);if(!"UNCERTAIN".equals(s.publishStatus)||s.publishKey==null)throw new BizException(1403,"仅待确认的发布请求可以关联平台计划");
        var a=store.attempt(s.tenantId,s.publishKey).orElseThrow();var remote=qc.detail(tenant,s.accountId,s.awemeId,req.qcPlanId);
        if(!s.name.equals(remote.path("name").asText())||!s.awemeId.equals(remote.path("aweme_id").asText()))throw new BizException(1403,"平台计划名称或抖音号与当前草稿不匹配");
        var remoteProducts=new HashSet<String>();for(var p:remote.path("product_ids"))remoteProducts.add(p.asText());
        for(var c:remote.path("multi_product_creative_list"))remoteProducts.add(c.path("product_id").asText());
        if(!remoteProducts.equals(new HashSet<>(ids(s))))throw new BizException(1403,"平台计划的商品与当前草稿不匹配，不能关联");
        store.finishPublish(s.tenantId,s.publishKey,"CREATED",req.qcPlanId,null);accepted(a,req.qcPlanId);return get(tenant,id);
    }
    private static void recordFeedback(CreationState s,String material,String action,Feedback f) {
        if(f.description!=null&&f.description.length()>50||f.reasons!=null&&(f.reasons.size()>10||f.reasons.stream().anyMatch(x->x==null||x.length()>100)))throw new BizException(1503,"原因或说明过长");
        s.feedback.add(new CreationState.FeedbackEvent(material,action,f.reasons==null?List.of():List.copyOf(f.reasons),f.description,TenantContext.userId().value(),Instant.now().toString()));
    }
    private static Map<String,Object> engineProduct(CreationState.Product p){var m=new LinkedHashMap<String,Object>();m.put("id",p.qcProductId());m.put("name",p.name());m.put("category",p.category());if(p.userPrice()!=null||p.price()!=null)m.put("price",p.userPrice()!=null?p.userPrice():p.price());if(p.priceInfo()!=null){m.put("price_min",p.priceInfo().minPrice());m.put("price_max",p.priceInfo().maxPrice());}if(p.stock()!=null)m.put("inventory",p.stock());if(p.sales()!=null)m.put("sales",p.sales());if(p.imageUrl()!=null)m.put("image_url",p.imageUrl());m.put("availability",Map.of("status",p.unavailableReasons()!=null&&!p.unavailableReasons().isEmpty()?"unavailable":"available","reasons",p.unavailableReasons()==null?List.of():p.unavailableReasons()));return m;}
    private static List<Map<String,Object>> engineProducts(CreationState s){return s.products.stream().map(CreationWorkflowService::engineProduct).toList();}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
