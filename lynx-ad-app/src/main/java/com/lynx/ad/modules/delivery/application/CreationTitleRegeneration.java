package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.CreationAssetImporter;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.ProductTitlePool;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import org.springframework.stereotype.Service;

/** One text-only request; after an ambiguous POST all recovery is read-only. */
@Service
public class CreationTitleRegeneration {
    private final CreationStore store;private final CreationEngineClient engine;private final CreationAssetImporter importer;
    public CreationTitleRegeneration(CreationStore store,CreationEngineClient engine,CreationAssetImporter importer){this.store=store;this.engine=engine;this.importer=importer;}
    public CreationState submit(CreationState s,String product,String kind,int index,String original) {
        return submit(s,product,kind,index,original,false,UUID.randomUUID().toString());
    }
    public CreationState add(CreationState s,String product,String kind) {
        return add(s,product,kind,UUID.randomUUID().toString());
    }
    public CreationState add(CreationState s,String product,String kind,String request) {
        return submit(s,product,kind,0,"",true,request);
    }
    private CreationState submit(CreationState s,String product,String kind,int index,String original,boolean addition,String request) {
        var p=s.products.stream().filter(v->product.equals(v.qcProductId())).findFirst().orElseThrow(()->new BizException(1503,"商品不属于当前草稿"));
        boolean card="CARD".equals(kind);var before=new ArrayList<>(ProductTitlePool.values(ProductTitlePool.of(s,product),card));
        if(addition) {
            var pool=ProductTitlePool.of(s,product);var all=new HashSet<>(pool.customTitles);all.addAll(pool.cardTitles);
            if(before.size()>=com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy.RULES.customTitleMaximum()||all.size()>=30)
                throw new BizException(1503,"已达到当前商品的标题数量上限");
        } else if(index<0||index>=before.size()||!before.get(index).equals(original))throw new BizException(1404,"标题已变化，请读取最新内容后重试");
        // The copy-only engine accepts a reference title even for an empty pool. It is never inserted as a placeholder.
        String reference=addition?(before.isEmpty()?p.name().substring(0,Math.min(p.name().length(),card?30:55)):before.getFirst()):original;
        var existing=new ArrayList<>(before);if(existing.isEmpty())existing.add(reference);
        var reserved=store.mutate(s.tenantId,s.planId,s.revision,current->{
            if(current.titleGenerationPending())throw new BizException(1404,"标题正在生成，请等待本次结果");
            var r=new CreationState.TitleGeneration();r.requestId=request;r.productId=product;r.kind=kind;r.index=index;r.original=reference;r.before=before;r.addition=addition;r.sentAt=System.currentTimeMillis();r.message=addition?"正在生成新增标题":"正在重新生成标题";
            current.titleGeneration=r;current.changed();
        });
        JsonNode result;
        try {result=engine.post("/api/v1/capabilities/generation/titles",Map.of("store_id",s.tenantId,"account_id",Objects.toString(s.accountId,""),"request_id",request,"product_id",product,"kind",kind,
                "original",reference,"existing_titles",existing,"product_name",p.name(),"category",Objects.toString(p.category(),"")));}
        catch(BizException error) {
            if(error.code()!=8003)return finish(reserved,request,"FAILED","标题生成未完成，原标题已保留，请重试。");
            return finish(reserved,request,"PROCESSING","标题结果正在核查，原标题已保留。");
        }
        return apply(reserved,request,result);
    }
    public CreationState refresh(CreationState s) {
        if(!s.titleGenerationPending())return s;var r=s.titleGeneration;
        try {
            var result=engine.find("/api/v1/capabilities/generation/titles/"+CreationEngineClient.query(r.requestId)+"?store_id="+CreationEngineClient.query(s.tenantId));
            if(result!=null)return apply(s,r.requestId,result);
            if(System.currentTimeMillis()-r.sentAt>300_000)return finish(s,r.requestId,"FAILED","本次标题生成未取得结果，原标题已保留，可重试。");
        }catch(BizException ignored){/* Only read the same request. Never resubmit after a timeout. */}
        return s;
    }
    private CreationState apply(CreationState s,String request,JsonNode result) {
        var r=s.titleGeneration;
        if(!request.equals(result.path("request_id").asText()))return finish(s,request,"FAILED","标题回执不匹配，原标题已保留。");
        String status=result.path("status").asText();
        if("PROCESSING".equals(status))return s;
        if(!"SUCCEEDED".equals(status))return finish(s,request,"FAILED","标题生成未完成，原标题已保留，请重试。");
        String title=result.path("title").asText().strip();boolean card="CARD".equals(r.kind);
        if(!r.productId.equals(result.path("product_id").asText())||!r.kind.equals(result.path("kind").asText())||!r.original.equals(result.path("original").asText())||r.before.contains(title)||r.original.equals(title))
            return finish(s,request,"FAILED","生成标题重复或归属不匹配，原标题已保留。");
        var titles=new ArrayList<>(r.before);if(r.addition)titles.add(title);else titles.set(r.index,title);
        try{ProductTitlePool.validate(titles,card);}catch(BizException invalid){return finish(s,request,"FAILED",invalid.getMessage()+"，原标题已保留。");}
        var poolBefore=ProductTitlePool.of(s,r.productId);var all=new HashSet<>(card?poolBefore.customTitles:poolBefore.cardTitles);all.addAll(titles);
        if(all.size()>30)return finish(s,request,"FAILED","每个商品最多使用30条不同标题，已有标题已保留。");
        // This registers only a TEXT review asset; there is no visual upload or engine media job.
        String text=importer.ingest(new TenantId(s.tenantId),new UserId(s.userId),s.planId+":title:"+request,String.join("\n",titles),"TEXT",null,false);
        return store.mutate(s.tenantId,s.planId,null,current->{
            if(!owns(current,request))return;
            var pool=ProductTitlePool.of(current,r.productId);
            if(!ProductTitlePool.values(pool,card).equals(r.before)||!Set.of("NONE","FAILED").contains(current.publishStatus)||current.publishedEdit!=null&&!"DRAFT".equals(current.publishedEdit.status)) {
                current.titleGeneration.status="FAILED";current.titleGeneration.message="标题或计划已变化，生成结果未覆盖当前内容。";current.changed();return;
            }
            var generated=ProductTitlePool.generated(pool,card);generated.retainAll(titles);generated.add(title);
            if(card){pool.cardTitles=titles;pool.cardTextAssetId=text;}else{pool.customTitles=titles;pool.customTextAssetId=text;}
            current.titlePools.put(r.productId,pool);current.titleGeneration.status="SUCCEEDED";current.titleGeneration.message=r.addition?"标题已新增":"标题已重新生成";current.mediaChanged();
        });
    }
    private static boolean owns(CreationState s,String request){return s.titleGenerationPending()&&request.equals(s.titleGeneration.requestId);}
    private CreationState finish(CreationState s,String request,String status,String message){return store.mutate(s.tenantId,s.planId,null,current->{if(owns(current,request)){current.titleGeneration.status=status;current.titleGeneration.message=current.titleGeneration.addition?message.replace("原标题","已有标题"):message;current.changed();}});}
}
