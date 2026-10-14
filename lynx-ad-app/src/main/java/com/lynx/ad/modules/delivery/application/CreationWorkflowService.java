package com.lynx.ad.modules.delivery.application;

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
    private PlanTransferStore transfers;
    private MaterialCreativeContextService creativeContexts;
    @org.springframework.beans.factory.annotation.Autowired public void creativeContexts(MaterialCreativeContextService value){creativeContexts=value;}
    @org.springframework.beans.factory.annotation.Autowired public void transferStore(PlanTransferStore value){transfers=value;}
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
        this.store=store;this.plans=plans;this.bindings=bindings;this.qc=qc;this.engine=engine;this.json=json;this.importer=importer;this.assets=assets;this.carousels=carousels;this.payloads=payloads;this.billing=billing;this.tx=new TransactionTemplate(manager);
    }
    public record Selection(String planId,Integer revision,String accountId,String awemeId,List<String> productIds) {}
    public record Goal(int revision,String name,String smartBidType,BigDecimal budget,BigDecimal roi2Goal,String scheduleType,String startDate,String endDate) {}
    public record Version(int revision) {}
    public record Step(int revision,Integer step) {}
    public record Visibility(int revision,Boolean hideInAweme) {}
    public record Generate(int revision,List<String> productIds) {}
    public record Addition(int revision,String productId,String type,String description,String requestId) {}
    public record MaterialSelection(int revision,String productId,String type,String videoAssetId,String coverAssetId,List<String> imageAssetIds,String carouselId,List<String> titles) {}
    public record Feedback(int revision,List<String> reasons,String description) {}
    public record Publish(int revision,int packageVersion,String requestKey) {}
    public record Titles(int revision,List<String> titles) {}
    public record Reconcile(String qcPlanId) {}
    public CreationState get(TenantId tenant,String requestedId) {
        String id=sharedId(tenant,requestedId,false);
        var s=store.get(tenant.value(),id);
        s=refreshGeneratedFiles(tenant,s);
        s.products=new ArrayList<>(currentProductPrices(tenant,s));
        for(var m:s.materials) {
            m.previewUrl=previewUrl(tenant,m.videoAssetId!=null?m.videoAssetId:m.audioAssetId);
            m.posterUrl=previewUrl(tenant,m.coverAssetId);
            m.imageUrls=new ArrayList<>();
            for(String ref:m.imageAssetIds){String url=previewUrl(tenant,ref);if(url!=null&&!url.isBlank())m.imageUrls.add(url);}
            if(m.posterUrl==null&&!m.imageUrls.isEmpty())m.posterUrl=m.imageUrls.getFirst();
            // Signed engine download URLs are never returned to the browser.
            m.sourceUrls.clear();m.importedAssets.clear();
        }
        if(creativeContexts!=null)creativeContexts.hydrate(s);
        return s;
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
    private String previewUrl(TenantId tenant,String asset) {
        if(asset==null)return null;
        try{return assets.detail(tenant,asset).url();}catch(BizException ignored){return null;}
    }
    private List<CreationState.Product> currentProductPrices(TenantId tenant,CreationState state) {
        if(qc==null || state.products.isEmpty())return state.products;
        try {
            var next=qc.refreshPrices(tenant,state.accountId,state.awemeId,state.products);
            // A metadata refresh must never change the saved product membership.
            if(next!=null && next.stream().map(CreationState.Product::qcProductId).toList().equals(ids(state)))return next;
        }catch(BizException unavailable){/* Saved plans remain readable after authorization expires. */}
        return state.products.stream().map(p->new CreationState.Product(p.productId(),p.qcProductId(),p.name(),p.price(),p.userPrice(),p.imageUrl(),p.category(),p.stock(),p.tags(),p.unavailableReasons(),p.priceInfo()==null?null:p.priceInfo().staleCopy(),p.channelId())).toList();
    }
    public CreationState select(TenantId tenant,Selection requested) {
        var request=new Selection(sharedId(tenant,requested.planId,true),requested.revision,requested.accountId,requested.awemeId,requested.productIds);
        writable(tenant,WriteAction.SAVE_PLAN);
        var selected=qc.selected(tenant,request.accountId,request.awemeId,request.productIds);
        if(request.planId==null||request.planId.isBlank())return tx.execute(status->{
            var setting=new DeliverySetting(SmartBidType.CUSTOM,Money.zero(),Optional.empty(),Schedule.fromNow(),AudienceJson.empty());
            String name=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_商品全域投放_HH:mm:ss"))+"_"+UUID.randomUUID().toString().substring(0,3);
            var plan=AdPlan.draft(tenant,new AccountId(request.accountId),new AwemeId(request.awemeId),new PlanName(name),setting,request.productIds.stream().map(QcProductId::new).toList());
            plans.save(plan);bindProducts(plan);
            var s=new CreationState();s.planId=plan.id().value();s.tenantId=tenant.value();s.userId=TenantContext.userId().value();
            s.accountId=request.accountId;s.awemeId=request.awemeId;s.name=name;s.products=new ArrayList<>(selected);store.insert(s);return s;
        });
        return store.mutate(tenant.value(),request.planId,request.revision,s->{
            editable(s);if(!s.accountId.equals(request.accountId)||!s.awemeId.equals(request.awemeId))throw new BizException(1403,"切换账户后请建立新的草稿");
            var next=selected.stream().map(CreationState.Product::qcProductId).toList();var previous=s.products.stream().map(CreationState.Product::qcProductId).toList();
            if(!previous.equals(next)) {
                s.analysisStatus="NONE";s.analysis=null;s.enginePlanId=null;s.engineJobId=null;s.groups=null;s.recommendations=null;
                s.generationStatus="NONE";s.progress=0;s.generationStage=null;s.generationCounts.clear();s.generationIssues.clear();s.materials.clear();s.removedMaterialIds.clear();s.mediaChanged();s.step=1;
            }
            s.products=new ArrayList<>(selected);var p=plan(s);p.replaceProducts(request.productIds.stream().map(QcProductId::new).toList());plans.save(p);bindProducts(p);
        });
    }
    public CreationState analyze(TenantId tenant,String requestedId,Version v) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        var snapshot=store.get(tenant.value(),id);
        var currentProducts=currentProductPrices(tenant,snapshot);
        return store.mutate(tenant.value(),id,v.revision,s->{editable(s);s.products=new ArrayList<>(currentProducts);s.analysisStatus="QUEUED";s.analysisError=null;s.changed();});
    }
    public CreationState recommendations(TenantId tenant,String requestedId) {
        String id=sharedId(tenant,requestedId,true);
        var s=store.get(tenant.value(),id);var response=qc.recommendations(tenant,s.accountId,s.awemeId,ids(s));
        return store.mutate(tenant.value(),id,s.revision,state->state.recommendations=response);
    }
    public CreationState goal(TenantId tenant,String requestedId,Goal g) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        return store.mutate(tenant.value(),id,g.revision,s->{
            editable(s);if(!"SUCCEEDED".equals(s.analysisStatus))throw new BizException(1403,"请先完成商品分析");
            if(g.smartBidType==null||!Set.of("CUSTOM","CONSERVATIVE","SMART_BID_CUSTOM","SMART_BID_CONSERVATIVE").contains(g.smartBidType))throw new BizException(1402,"请选择有效的投放方式");
            s.name=g.name==null?"":g.name.trim();s.smartBidType=SmartBidType.fromOcean(g.smartBidType).name();s.budget=g.budget;
            s.roi2Goal="CUSTOM".equals(s.smartBidType)?g.roi2Goal:null;s.scheduleType=g.scheduleType;s.startDate=g.startDate;s.endDate=g.endDate;
            CreationPayloadBuilder.settings(s);
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
            if(s.qcPlanId!=null||!Set.of("NONE","FAILED").contains(s.publishStatus))throw new BizException(1403,"计划正在发布或已发布，不可修改草稿");
            if(request.step>=2&&!"SUCCEEDED".equals(s.analysisStatus))throw new BizException(1403,"请先完成商品分析");
            if(request.step==3&&s.step!=3)CreationPayloadBuilder.settings(s);
            // Navigation changes no creative content and must preserve an already confirmed package.
            if(s.step!=request.step){s.step=request.step;s.revision++;}
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
    public CreationState generate(TenantId tenant,String requestedId,Generate g) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        return store.mutate(tenant.value(),id,g.revision,s->{
            editable(s);CreationPayloadBuilder.settings(s);
            if(!"SUCCEEDED".equals(s.analysisStatus))throw new BizException(1403,"请先完成商品分析");
            if(g.productIds==null||g.productIds.isEmpty()||!ids(s).containsAll(g.productIds))throw new BizException(1503,"请选择当前计划中的商品生成素材");
            s.generationProductIds=new ArrayList<>(new LinkedHashSet<>(g.productIds));s.generationStatus="QUEUED";s.generationMessage="等待 AI 引擎生成";s.previousEngineJobId=s.engineJobId;s.engineJobId=null;s.engineTerminalStatus=null;s.pendingRegenerationId=null;s.progress=0;s.changed();
            s.generationStage="queued";s.generationCounts.clear();s.generationIssues.clear();
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
            if(s.revision!=request.revision)throw new BizException(1404,"草稿已更新，请刷新后重试");
            editable(s);CreationPayloadBuilder.settings(s);
            if(!"SUCCEEDED".equals(s.analysisStatus)||s.enginePlanId==null)throw new BizException(1403,"请先完成商品分析");
            var product=s.products.stream().filter(p->Objects.equals(p.qcProductId(),request.productId)).findFirst().orElseThrow(()->new BizException(1503,"商品不属于当前计划"));
            if(product.imageUrl()==null||product.imageUrl().isBlank())throw new BizException(1503,"商品主图缺失，无法生成素材");
            if(s.materials.size()>=100)throw new BizException(1503,"每个计划最多使用100个素材");
            var body=new LinkedHashMap<String,Object>();body.put("store_id",s.tenantId);body.put("ai_plan_task_id",s.enginePlanId);body.put("request_id",request.requestId);
            body.put("product_id",request.productId);body.put("target",additionTarget(request.type));body.put("instruction",description);
            body.put("budget",s.budget);if(s.roi2Goal!=null)body.put("roi",s.roi2Goal);
            var p=engineProduct(product);p.put("image_urls",List.of(product.imageUrl()));body.put("product",p);
            if(creativeContexts!=null)creativeContexts.hydrate(s);
            body.put("existing_materials",s.materials.stream().filter(m->request.productId.equals(m.productId)).map(m->{var previous=new LinkedHashMap<String,Object>();previous.put("product_id",m.productId);previous.put("material_id",m.engineMaterialId==null?m.id:m.engineMaterialId);previous.put("material_type",additionTarget(m.type));previous.put("title",m.title==null?"":m.title);if(m.creativeProfile!=null)previous.put("creative_profile",m.creativeProfile);previous.put("copy_excerpt",m.text==null?"":m.text.substring(0,Math.min(500,m.text.length())));return previous;}).toList());
            for(int n=s.materials.size()-1;n>=0;n--){var m=s.materials.get(n);if(request.productId.equals(m.productId)&&m.engineJobId!=null&&m.groupId!=null){body.put("job_id",m.engineJobId);body.put("group_id",m.groupId);break;}}
            var r=new CreationState.GenerationAddition();r.id=request.requestId;r.productId=request.productId;r.type=request.type;r.description=description;r.status="QUEUED";r.createdAt=Instant.now().toString();r.engineRequest=json.valueToTree(body);
            s.generationAdditions.add(r);s.pendingAdditionId=r.id;s.generationProductIds=new ArrayList<>(List.of(r.productId));s.generationStatus="QUEUED";s.generationMessage="等待生成新增素材";s.generationStage="queued";s.progress=0;
            s.generationCounts=new ArrayList<>(List.of(new CreationState.GenerationCount(r.type,0,1)));s.generationIssues.clear();s.revision++;
        });
    }
    private static String additionTarget(String type){return switch(type){case "VIDEO"->"video";case "IMAGE"->"product_card";case "CAROUSEL"->"image_text";default->throw new BizException(1503,"不支持的素材类型");};}
    private static CreationState.GenerationAddition additionRecord(CreationState s,String id){return s.generationAdditions.stream().filter(r->r.id.equals(id)).findFirst().orElseThrow(()->new BizException(1503,"新增素材任务不存在"));}
    public CreationState add(TenantId tenant,String requestedId,MaterialSelection request) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);var initial=store.get(tenant.value(),id);editable(initial);
        if(initial.revision!=request.revision)throw new BizException(1404,"草稿已更新，请刷新后重试");
        if(!ids(initial).contains(request.productId))throw new BizException(1503,"素材对应商品不属于当前计划");
        var m=new CreationState.Material();m.id=UUID.randomUUID().toString();m.productId=request.productId;m.type=request.type;
        m.videoAssetId=request.videoAssetId;m.coverAssetId=request.coverAssetId;m.imageAssetIds=request.imageAssetIds==null?new ArrayList<>():new ArrayList<>(request.imageAssetIds);
        m.carouselId=request.carouselId;m.titles=request.titles==null?new ArrayList<>():request.titles.stream().filter(Objects::nonNull).map(String::trim).filter(x->!x.isEmpty()).distinct().toList();
        if(m.titles.isEmpty()||m.titles.size()>29||m.titles.stream().anyMatch(x->x.length()>55))throw new BizException(1503,"请填写 1–29 条自定义标题，每条最多 55 字");
        m.title=m.titles.getFirst();
        List<String> targetAssets=new ArrayList<>();
        if("VIDEO".equals(m.type)){requireKind(tenant,m.videoAssetId,"VIDEO");requireKind(tenant,m.coverAssetId,"IMAGE");targetAssets.addAll(List.of(m.videoAssetId,m.coverAssetId));}
        else if("IMAGE".equals(m.type)){if(m.imageAssetIds.isEmpty())throw new BizException(1503,"请选择图片");for(String a:m.imageAssetIds){requireKind(tenant,a,"IMAGE");targetAssets.add(a);}}
        else if("CAROUSEL".equals(m.type)){var b=carousels.detail(tenant,m.carouselId);if(!Set.of("APPROVED","NOT_REQUIRED").contains(b.aiReview().status()))throw new BizException(1503,"图文质检未通过或引擎图文尚未就绪");m.imageAssetIds=b.parts().stream().filter(p->p.role().equals("IMAGE")).map(p->p.asset().id()).toList();m.audioAssetId=b.parts().stream().filter(p->p.role().equals("AUDIO")).findFirst().orElseThrow().asset().id();targetAssets.addAll(b.parts().stream().filter(p->!p.role().equals("TEXT")).map(p->p.asset().id()).toList());}
        else throw new BizException(1503,"不支持的素材类型");
        for(String a:targetAssets)assets.ensureTarget(tenant,a,initial.accountId);
        m.textAssetId=importer.ingest(tenant,new UserId(initial.userId),id+":"+m.id+":text",String.join("\n",m.titles),"TEXT",null,false);
        return store.mutate(tenant.value(),id,request.revision,s->{editable(s);if(s.materials.size()>=100)throw new BizException(1503,"每个计划最多使用 100 个素材");s.materials.add(m);s.mediaChanged();});
    }
    public CreationState remove(TenantId tenant,String requestedId,String material,Feedback feedback) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        return store.mutate(tenant.value(),id,feedback.revision,s->{editable(s);var m=find(s,material);recordFeedback(s,material,"delete",feedback);if(creativeContexts!=null)creativeContexts.removed(s,m);s.removedMaterialIds.add(m.id);s.materials.remove(m);s.mediaChanged();});
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
        var titles=request.titles==null?List.<String>of():request.titles.stream().filter(Objects::nonNull).map(String::trim).filter(x->!x.isBlank()).distinct().toList();
        if(titles.isEmpty()||titles.size()>29||titles.stream().anyMatch(x->x.length()>55))throw new BizException(1503,"自定义标题须为 1–29 条，每条最多 55 字");
        String key=id+":"+material+":titles:"+hash(String.join("\n",titles));
        String text=importer.ingest(tenant,new UserId(s.userId),key,String.join("\n",titles),"TEXT",null,false);
        return store.mutate(tenant.value(),id,request.revision,state->{editable(state);var m=find(state,material);m.titles=new ArrayList<>(titles);m.title=titles.getFirst();m.textAssetId=text;m.status="PROCESSING";m.error=null;state.mediaChanged();});
    }
    public CreationState recheck(TenantId tenant,String requestedId,String material,Version v) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);
        return store.mutate(tenant.value(),id,v.revision,s->{editable(s);var m=find(s,material);m.status="PROCESSING";m.error=null;s.mediaChanged();});
    }
    public CreationState regenerate(TenantId tenant,String requestedId,String material,Feedback feedback) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);var s=store.get(tenant.value(),id);editable(s);var m=find(s,material);
        if(m.engineMaterialId==null||m.engineJobId==null)throw new BizException(1503,"该素材为本地上传，请重新上传替换；AI 素材可重新生成");
        store.mutate(tenant.value(),id,feedback.revision,state->{recordFeedback(state,material,"regenerate",feedback);state.engineJobId=m.engineJobId;state.engineTerminalStatus=null;state.pendingRegenerationId=m.id;state.generationProductIds=new ArrayList<>(List.of(m.productId));state.generationStatus="REVISING";state.generationMessage="正在提交素材重新生成";state.progress=0;state.generationStage="queued";state.generationCounts.clear();state.generationIssues.clear();state.changed();});
        try {
            String target=switch(m.type){case "VIDEO"->"video";case "IMAGE"->"product_card";default->"image_text";};
            var body=new LinkedHashMap<String,Object>();body.put("job_id",m.engineJobId);body.put("ai_plan_task_id",s.enginePlanId);body.put("store_id",s.tenantId);body.put("group_id",m.groupId);body.put("operation","regenerate");body.put("target",target);
            String instruction=String.join("、",feedback.reasons==null?List.of():feedback.reasons)+" "+(feedback.description==null?"":feedback.description);
            body.put("instruction",instruction.substring(0,Math.min(200,instruction.length())));
            engine.post("/api/v1/capabilities/generation/materials/"+CreationEngineClient.query(m.engineMaterialId)+"/revisions",body);
            return store.mutate(tenant.value(),id,null,state->{state.generationStatus="RUNNING";state.generationMessage="素材重新生成中，原素材暂时保留";});
        }catch(BizException e){store.mutate(tenant.value(),id,null,state->{state.generationStatus=e.code()==8003?"REVISING":"FAILED";state.generationMessage=e.getMessage();});throw e;}
    }
    public CreationState confirm(TenantId tenant,String requestedId,Version v) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.SAVE_PLAN);var s=store.get(tenant.value(),id);editable(s);
        for(var p:s.products)if(s.materials.stream().noneMatch(m->p.qcProductId().equals(m.productId)))throw new BizException(1503,"请为所有商品补齐素材");
        for(var m:s.materials)payloads.materialReady(s,m);
        return store.mutate(tenant.value(),id,v.revision,state->{editable(state);state.confirmedVersion=state.packageVersion;});
    }
    public CreationState publish(TenantId tenant,String requestedId,Publish request) {
        String id=sharedId(tenant,requestedId,true);
        writable(tenant,WriteAction.PUBLISH);
        if(request.requestKey==null||!request.requestKey.matches("[A-Za-z0-9_-]{16,64}"))throw new BizException(1402,"发布请求标识不合法");
        var replay=store.attempt(tenant.value(),request.requestKey);
        if(replay.isPresent()) {if(!id.equals(replay.get().planId())||request.revision!=replay.get().revision())throw new BizException(1404,"发布标识已用于其他计划版本");return get(tenant,id);}
        var s=store.get(tenant.value(),id);if(s.publishKey!=null&&Set.of("QUEUED","SENDING","UNCERTAIN","CREATED").contains(s.publishStatus))return get(tenant,id);editable(s);qc.selected(tenant,s.accountId,s.awemeId,ids(s));
        var body=payloads.build(s);String hash=hash(body.toString());
        return store.mutate(tenant.value(),id,request.revision,state->{
            if(state.publishKey!=null&&Set.of("QUEUED","SENDING","UNCERTAIN","CREATED").contains(state.publishStatus))return;
            editable(state);if(request.packageVersion!=state.packageVersion)throw new BizException(1404,"素材版本已变化");
            store.attempt(new CreationStore.Attempt(UUID.randomUUID().toString(),state.tenantId,id,state.revision,request.requestKey,hash,body.toString(),"QUEUED",null,null));
            state.publishKey=request.requestKey;state.publishStatus="QUEUED";state.publishError=null;state.step=4;
            var p=plan(state);p.apply(PlanStatus.SUBMITTING);plans.save(p);
        });
    }
    public void discard(TenantId tenant,String requestedId) {
        String id=sharedId(tenant,requestedId,true);
        var s=store.get(tenant.value(),id);if(!Set.of("NONE","FAILED").contains(s.publishStatus))throw new BizException(1403,"已提交发布的计划不能丢弃");
        store.mutate(tenant.value(),id,null,state->{if(!Set.of("NONE","FAILED").contains(state.publishStatus))throw new BizException(1403,"计划已提交");state.analysisStatus="CANCELLED";state.generationStatus="CANCELLED";});
        if(s.engineJobId!=null)try{engine.post("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(s.engineJobId)+"/control",Map.of("store_id",s.tenantId,"action","cancel"));}catch(BizException ignored){}
        tx.executeWithoutResult(status->{if(!plans.discardDraft(tenant,new PlanId(id)))throw new BizException(1403,"仅可丢弃未发布草稿");bindings.upsert(new PlanId(id),List.of());store.delete(tenant.value(),id);});
    }
    public void process(CreationState snapshot) {
        if(snapshot.pendingAdditionId!=null) {
            var request=additionRecord(snapshot,snapshot.pendingAdditionId);
            if(Set.of("QUEUED","SUBMITTING","RUNNING","IMPORTING","UNCERTAIN").contains(request.status)){processAddition(snapshot,request);return;}
        }
        var tenant=new TenantId(snapshot.tenantId);String id=snapshot.planId;
        if(Set.of("SUBMITTING","REVISING","UNCERTAIN").contains(snapshot.generationStatus)) {
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
        if(Set.of("QUEUED","RUNNING").contains(snapshot.analysisStatus)) {
            store.mutate(snapshot.tenantId,id,null,s->s.analysisStatus="RUNNING");
            try {
                String enginePlan=snapshot.enginePlanId;
                if(enginePlan==null) {
                    var response=engine.post("/api/v1/plan-tasks",Map.of("platform","douyin","store_id",snapshot.tenantId,"account",Map.of("account_id",snapshot.accountId,"aweme_id",snapshot.awemeId),"operator",Map.of("user_id",snapshot.userId),"metadata",Map.of("lingtou_plan_id",id)));
                    enginePlan=response.path("ai_plan_task_id").asText(response.path("id").asText());
                    if(enginePlan.isBlank())throw new BizException(8002,"引擎未返回计划任务 ID");
                    final String registered=enginePlan;store.mutate(snapshot.tenantId,id,null,s->s.enginePlanId=registered);
                }
                var response=engine.post("/api/v1/capabilities/perception/products/analyze",Map.of("ai_plan_task_id",enginePlan,"store_id",snapshot.tenantId,"products",engineProducts(snapshot),"refresh",false));
                if(!response.isObject()||!response.has("product_set"))throw new BizException(8002,"引擎未返回有效商品分析");
                store.mutate(snapshot.tenantId,id,null,s->{if("RUNNING".equals(s.analysisStatus)){s.analysis=response;s.analysisStatus="SUCCEEDED";s.analysisError=null;}});
            }catch(BizException e){store.mutate(snapshot.tenantId,id,null,s->{if("RUNNING".equals(s.analysisStatus)){s.analysisStatus="FAILED";s.analysisError=e.getMessage();}});}
            return;
        }
        if("QUEUED".equals(snapshot.generationStatus)) {
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
            }catch(BizException e){store.mutate(snapshot.tenantId,id,null,s->{if("SUBMITTING".equals(s.generationStatus)){s.generationStatus=e.code()==8003?"UNCERTAIN":"FAILED";s.generationMessage=e.getMessage();}});}
            return;
        }
        var current=store.get(snapshot.tenantId,id);
        boolean recoveringSummary="FAILED".equals(current.generationStatus)&&current.materials.stream().anyMatch(m->"PROCESSING".equals(m.status));
        if(current.engineJobId!=null && (Set.of("RUNNING","IMPORTING").contains(current.generationStatus)||recoveringSummary)) {
            var job=engine.get("/api/v1/capabilities/generation/jobs/"+CreationEngineClient.query(current.engineJobId)+"?store_id="+CreationEngineClient.query(current.tenantId));
            if(!current.engineJobId.equals(job.path("job_id").asText()))throw new BizException(8002,"生成任务响应不匹配");
            var ready=new ArrayList<CreationState.Material>();
            for(var group:job.path("groups")) {
                String groupId=group.path("group_id").asText();int index;
                try{index=Integer.parseInt(groupId.substring("group-".length()))-1;}catch(Exception ignored){continue;}
                if(index<0||index>=current.products.size())continue;
                for(var item:group.path("materials")) {
                    if(!"v1".equals(item.path("version").asText())||!"succeeded".equals(item.path("status").asText()))continue;
                    String mid=item.path("material_id").asText();if(mid.isBlank()||current.removedMaterialIds.contains(mid)||current.materials.stream().anyMatch(m->m.id.equals(mid)))continue;
                    var m=new CreationState.Material();m.id=mid;m.engineMaterialId=mid;m.engineJobId=current.engineJobId;m.groupId=groupId;m.productId=current.products.get(index).qcProductId();
                    m.type=switch(item.path("material_type").asText()){case "video"->"VIDEO";case "image_text"->"CAROUSEL";case "product_card"->"IMAGE";default->"";};if(m.type.isEmpty())continue;
                    m.title=item.path("content").path("title").asText();m.text=item.path("content").path("text").asText();
                    m.titles=new ArrayList<>(List.of(m.title));var urls=item.path("urls");
                    if("VIDEO".equals(m.type)){m.sourceUrls.put("VIDEO",urls.path("video_url").asText());m.sourceUrls.put("COVER",urls.path("cover_url").asText());}
                    else {int n=0;for(var url:urls.path("image_urls"))m.sourceUrls.put("IMAGE_"+n++,url.asText());}
                    if("CAROUSEL".equals(m.type))m.sourceUrls.put("AUDIO",urls.path("voice_url").asText());
                    String parent=item.path("detail").path("parent_material_id").asText(null);m.replacesId=parent;
                    ready.add(m);
                }
            }
            String status=job.path("status").asText();
            store.mutate(current.tenantId,id,null,s->{
                if(!Set.of("RUNNING","IMPORTING","FAILED").contains(s.generationStatus))return;
                for(var m:ready)if(!s.removedMaterialIds.contains(m.id)&&s.materials.stream().noneMatch(x->x.id.equals(m.id))&&s.materials.size()<100){s.materials.add(m);s.mediaChanged();}
                s.progress=Math.min(80,job.path("progress").path("percent").asInt(80));s.generationMessage=job.path("progress").path("message").asText("正在登记引擎素材并同步千川");
                updateGenerationSummary(s,job);
                s.generationStage=job.path("progress").path("stage").asText("generation");
                if(Set.of("succeeded","partial_failed","failed","cancelled").contains(status)){s.engineTerminalStatus=status;s.generationStatus="IMPORTING";s.generationStage="syncing";s.progress=80;}
            });
        }
        current=store.get(snapshot.tenantId,id);
        for(var material:current.materials)if(!"READY".equals(material.status)&&!"FAILED".equals(material.status))processMaterial(current,material);
        store.mutate(snapshot.tenantId,id,null,s->{
            if(!Set.of("IMPORTING","FAILED").contains(s.generationStatus)||s.engineTerminalStatus==null)return;
            long finished=s.materials.stream().filter(m->Set.of("READY","FAILED").contains(m.status)).count();
            if(finished<s.materials.size()){s.progress=80+(int)(19*finished/s.materials.size());return;}
            boolean all=s.generationProductIds.stream().allMatch(p->s.materials.stream().anyMatch(m->m.productId.equals(p)&&"READY".equals(m.status)));
            boolean replacement=s.pendingRegenerationId==null||s.materials.stream().anyMatch(m->s.pendingRegenerationId.equals(m.replacesId)&&"READY".equals(m.status));
            boolean succeeded=all&&replacement&&s.materials.stream().allMatch(m->"READY".equals(m.status))&&"succeeded".equals(s.engineTerminalStatus);
            s.generationStatus=succeeded?"SUCCEEDED":"FAILED";s.progress=succeeded?100:80;s.generationMessage=succeeded?"素材已生成并同步千川":"部分素材生成或同步失败，已保留原有可用素材；可重新生成或从素材库补齐";
            s.generationStage="finished";
        });
    }
    private void processAddition(CreationState snapshot,CreationState.GenerationAddition request) {
        String tenant=snapshot.tenantId,id=snapshot.planId,rid=request.id;
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
            var m=new CreationState.Material();m.id=mid;m.engineMaterialId=mid;m.engineJobId=request.engineJobId;m.groupId=group.path("group_id").asText();m.productId=request.productId;m.type=request.type;
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
                    if(s.materials.size()>=100)throw new BizException(1503,"素材数量已达上限");s.materials.add(m);s.mediaChanged();
                }
                r.status="IMPORTING";s.generationStatus="IMPORTING";s.generationStage="syncing";
            }
            s.progress=outputs.isEmpty()?Math.min(80,job.path("progress").path("percent").asInt()):80;
            s.generationMessage=outputs.isEmpty()?job.path("progress").path("message").asText("新增素材生成中"):"新增素材已生成，正在同步千川";
        });
        var current=store.get(tenant,id);String currentMaterialId=additionRecord(current,rid).materialId;
        var material=current.materials.stream().filter(m->Objects.equals(m.id,currentMaterialId)).findFirst().orElse(null);
        if(material!=null&&!Set.of("READY","FAILED").contains(material.status))processMaterial(current,material);
        current=store.get(tenant,id);String savedMaterialId=additionRecord(current,rid).materialId;
        final var saved=current.materials.stream().filter(m->Objects.equals(m.id,savedMaterialId)).findFirst().orElse(null);
        boolean ready=saved!=null&&"READY".equals(saved.status);
        if(terminal&&(saved==null||Set.of("READY","FAILED").contains(saved.status))) {
            if(ready&&"succeeded".equals(job.path("status").asText()))store.mutate(tenant,id,null,s->{var done=additionRecord(s,rid);done.status="SUCCEEDED";done.error=null;s.generationStatus="SUCCEEDED";s.generationMessage="新增素材已生成并同步千川";s.generationStage="finished";s.progress=100;s.generationCounts=new ArrayList<>(List.of(new CreationState.GenerationCount(done.type,1,1)));});
            else failAddition(tenant,id,rid,saved!=null&&saved.error!=null?saved.error:"本次新增素材未完成，原有素材已保留");
        }
    }
    private void failAddition(String tenant,String id,String rid,String error) {
        store.mutate(tenant,id,null,s->{var r=additionRecord(s,rid);r.status="FAILED";r.error=error;s.generationStatus="FAILED";s.generationMessage=error;s.generationStage="finished";
            var product=s.products.stream().filter(p->p.qcProductId().equals(r.productId)).findFirst().orElse(null);s.generationIssues=new ArrayList<>(List.of(new CreationState.GenerationIssue(r.productId,product==null?r.productId:product.name(),error)));});
    }
    private static void updateGenerationSummary(CreationState s,JsonNode job) {
        s.generationCounts.clear();s.generationIssues.clear();
        var types=Map.of("VIDEO","video","IMAGE","product_card","CAROUSEL","image_text");
        var expectedKeys=Map.of("VIDEO","videos","IMAGE","product_cards","CAROUSEL","image_texts");
        boolean known=job.path("groups").isArray()&&!job.path("groups").isEmpty();
        for(var group:job.path("groups")) {
            var expected=group.path("expected");
            // Planning failures can have no counts. Do not invent totals for those products.
            if(!expected.isObject()||expected.path("videos").asInt()+expected.path("product_cards").asInt()+expected.path("image_texts").asInt()==0)known=false;
            boolean unfinishedGroup=Set.of("failed","partial_failed","cancelled").contains(job.path("status").asText())&&!"succeeded".equals(group.path("status").asText());
            if(unfinishedGroup||Set.of("failed","partial_failed","cancelled").contains(group.path("status").asText())) {
                try {
                    int index=Integer.parseInt(group.path("group_id").asText().replace("group-",""))-1;
                    if(index>=0&&index<s.products.size()) {
                        var p=s.products.get(index);
                        boolean hasResult=false;for(var m:group.path("materials"))if("v1".equals(m.path("version").asText())&&"succeeded".equals(m.path("status").asText()))hasResult=true;
                        String message=hasResult?"部分素材生成失败，已保留成功结果":"尚未生成可用素材，请重试或从素材库补齐";
                        s.generationIssues.add(new CreationState.GenerationIssue(p.qcProductId(),p.name(),message));
                    }
                }catch(NumberFormatException ignored){}
            }
        }
        for(String type:List.of("VIDEO","IMAGE","CAROUSEL")) {
            int completed=0,total=0;
            for(var group:job.path("groups")) {
                total+=group.path("expected").path(expectedKeys.get(type)).asInt(0);
                for(var m:group.path("materials"))if("v1".equals(m.path("version").asText())&&"succeeded".equals(m.path("status").asText())&&types.get(type).equals(m.path("material_type").asText()))completed++;
            }
            s.generationCounts.add(new CreationState.GenerationCount(type,completed,known?Math.max(total,completed):null));
        }
    }
    private void processMaterial(CreationState s,CreationState.Material m) {
        processMaterial(s,m,false);
    }
    /** Same proven import/sync path, with an independently persisted automatic job context. */
    public CreationState.Material importOptimizationMaterial(CreationState s,CreationState.Material m){processMaterial(s,m,true);return m;}
    private void processMaterial(CreationState s,CreationState.Material m,boolean automatic) {
        var tenant=new TenantId(s.tenantId);
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
            try{payloads.materialReady(s,m);m.status="READY";m.error=null;}
            catch(BizException e){m.status="PROCESSING";m.error=e.getMessage();
                var refs=new ArrayList<>(m.imageAssetIds);if(m.videoAssetId!=null)refs.add(m.videoAssetId);if(m.coverAssetId!=null)refs.add(m.coverAssetId);if(m.audioAssetId!=null)refs.add(m.audioAssetId);if(m.textAssetId!=null)refs.add(m.textAssetId);
                for(String ref:refs){var a=assets.detail(tenant,ref);if(a.aiReview()!=null&&Set.of("REJECTED","MANUAL_REVIEW","FAILED").contains(a.aiReview().status())){m.status="FAILED";m.error=a.aiReview().summary();}
                    var failedSync=a.syncs().stream().filter(x->s.accountId.equals(x.accountId())&&Set.of("FAILED","UNCERTAIN","PAUSED").contains(x.status())).findFirst();
                    if(failedSync.isPresent()){m.status="FAILED";m.error=failedSync.get().errorMessage()==null?"千川同步未完成，请在素材中心查看并重试":failedSync.get().errorMessage();}}
                if(Set.of(1523,1406).contains(e.code())){m.status="FAILED";m.error=e.getMessage();}
            }
        }catch(BizException e){m.status="FAILED";m.error=e.getMessage();}
        if("READY".equals(m.status)&&creativeContexts!=null)try{creativeContexts.record(s,m);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Material creative context registration pending: {}",m.id);}
        if(!automatic)store.mutate(s.tenantId,s.planId,null,state->{
            // A title edit or another user change may occur while an asset is being inspected.
            if (state.revision != s.revision) return;
            int i=-1;for(int n=0;n<state.materials.size();n++)if(state.materials.get(n).id.equals(m.id))i=n;
            if(i<0)return;state.materials.set(i,m);
            if("READY".equals(m.status)&&m.replacesId!=null){state.materials.removeIf(old->old.id.equals(m.replacesId));state.removedMaterialIds.add(m.replacesId);}
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
        }catch(BizException e){failed(a,e.code()==1523?"UNCERTAIN":"FAILED",e.getMessage());return;}
        catch(Exception e){failed(a,"UNCERTAIN","平台创建结果待确认，请核对平台计划，避免重复创建");return;}
        // Never downgrade a durable receipt when the following local transaction fails.
        store.finishPublish(a.tenant(),a.key(),"CREATED",qcId,null);
        accepted(a,qcId);
    }
    public void accepted(CreationStore.Attempt a,String qcId) {
        store.mutate(a.tenant(),a.planId(),null,s->{s.publishStatus="CREATED";s.qcPlanId=qcId;s.publishError=null;
            var p=plan(s);if(p.qcPlanId().isEmpty()){p.apply(PlanStatus.SUBMITTING);p.acceptExternalId(new QcPlanId(qcId));plans.save(p);}
        });
        store.applied(a);
    }
    public void failed(CreationStore.Attempt a,String state,String message) {
        var existing=store.attempt(a.tenant(),a.key());if(existing.isPresent()&&"CREATED".equals(existing.get().status())){accepted(existing.get(),existing.get().qcId());return;}
        store.finishPublish(a.tenant(),a.key(),state,null,message);
        store.mutate(a.tenant(),a.planId(),null,s->{s.publishStatus=state;s.publishError=message;
            var p=plan(s);if("FAILED".equals(state)){p.apply(PlanStatus.DRAFT);s.step=3;s.changed();}plans.save(p);});
    }
    private void writable(TenantId tenant,WriteAction action){billing.assertWritable(tenant,Instant.now(),action);}
    private AdPlan plan(CreationState s){return plans.findById(new TenantId(s.tenantId),new PlanId(s.planId)).orElseThrow(()->new BizException(1403,"计划不存在"));}
    private void bindProducts(AdPlan p){bindings.upsert(p.id(),p.productIds().stream().map(id->new PlanCreativeBinding(null,id,null,null,List.of())).toList());}
    private static List<String> ids(CreationState s){return s.products.stream().map(CreationState.Product::qcProductId).toList();}
    private void requireKind(TenantId t,String id,String kind){if(id==null||!kind.equals(assets.detail(t,id).kind()))throw new BizException(1503,"素材类型不匹配");}
    private static CreationState.Material find(CreationState s,String id){return s.materials.stream().filter(m->m.id.equals(id)).findFirst().orElseThrow(()->new BizException(1503,"素材不属于该计划"));}
    private static void editable(CreationState s){
        if(!Set.of("NONE","FAILED").contains(s.publishStatus))throw new BizException(1403,"计划正在发布或已发布，不可修改草稿");
        if(Set.of("QUEUED","RUNNING").contains(s.analysisStatus)||Set.of("QUEUED","SUBMITTING","REVISING","RUNNING","IMPORTING","UNCERTAIN").contains(s.generationStatus))throw new BizException(1403,"任务正在执行，请等待完成后修改");
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
    private static Map<String,Object> engineProduct(CreationState.Product p){var m=new LinkedHashMap<String,Object>();m.put("id",p.qcProductId());m.put("name",p.name());m.put("category",p.category());if(p.userPrice()!=null||p.price()!=null)m.put("price",p.userPrice()!=null?p.userPrice():p.price());if(p.priceInfo()!=null){m.put("price_min",p.priceInfo().minPrice());m.put("price_max",p.priceInfo().maxPrice());}return m;}
    private static List<Map<String,Object>> engineProducts(CreationState s){return s.products.stream().map(CreationWorkflowService::engineProduct).toList();}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
