package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.service.ProductRecommendationPolicy;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.DraftProductStore;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Read-only suggestions. Selection/save still revalidates platform eligibility and locks draft membership. */
@Service
public class ProductRecommendationService {
    private static final int PAGE_SIZE=100,MAX_PAGES=3,MAX_SCOPES=128;
    private final CreationQianchuanGateway qc;private final DraftProductStore drafts;private final AdPlanRepository plans;
    private final ProductCombinationService combinations; private final CreationStore creations;
    private Clock clock=Clock.systemUTC();
    private record Scope(String tenant,String account,String aweme) {}
    private record Pool(List<Product> products,long total,boolean partial,String warning,Instant fetchedAt) {}
    private static class Bucket { Pool pool; }
    private final Map<Scope,Bucket> cache=new ConcurrentHashMap<>();
    public record Result(String accountId,String awemeId,List<String> sourceProductIds,
                         List<ProductRecommendationPolicy.Suggestion> items,int scannedCount,long catalogTotal,
                         boolean partial,boolean cached,String fetchedAt,String method,String message) {}
    public ProductRecommendationService(CreationQianchuanGateway qc,DraftProductStore drafts,AdPlanRepository plans,ProductCombinationService combinations,CreationStore creations){this.qc=qc;this.drafts=drafts;this.plans=plans;this.combinations=combinations;this.creations=creations;}

    public Result recommend(TenantId tenant,String account,String aweme,List<String> ids,String draft,boolean refresh) {
        return recommend(tenant,account,aweme,ids,ids,draft,refresh);
    }
    public Result recommend(TenantId tenant,String account,String aweme,List<String> ids,List<String> excludeIds,String draft,boolean refresh) {
        if(account==null||account.isBlank()||aweme==null||aweme.isBlank())throw new BizException(1302,"请选择广告账户及抖音号");
        if(ids==null||ids.isEmpty()||ids.size()>10||new HashSet<>(ids).size()!=ids.size()
                ||ids.stream().anyMatch(id->id==null||!id.matches("[1-9][0-9]{0,18}")))throw new BizException(1302,"请选择 1–10 个不同的有效商品");
        var excluded=new HashSet<>(ids);
        if(excludeIds!=null){
            if(excludeIds.size()>10||excludeIds.stream().anyMatch(id->id==null||!id.matches("[1-9][0-9]{0,18}")))throw new BizException(1302,"已选商品列表不合法");
            excluded.addAll(excludeIds);
        }
        if(draft!=null&&!draft.isBlank()){
            var plan=plans.findById(tenant,new PlanId(draft)).orElse(null);
            if(plan!=null){
                if(!plan.accountId().value().equals(account)||!plan.awemeId().value().equals(aweme)||plan.status()!=PlanStatus.DRAFT||plan.qcPlanId().isPresent())throw new BizException(1403,"当前草稿与所选账户或抖音号不匹配");
            }else{
                var work=creations.get(tenant.value(),draft);
                if(work==null||work.publishedEdit==null||!account.equals(work.accountId)||!aweme.equals(work.awemeId)||!"DRAFT".equals(work.publishedEdit.status))throw new BizException(1403,"当前草稿不存在或不属于当前账户");
                if(plans.findById(tenant,new PlanId(work.publishedEdit.targetPlanId)).isEmpty())throw new BizException(1403,"原投放计划不存在或无权查看");
            }
        }
        // Authorization and selected facts are fresh even when the candidate catalog is cached.
        var selected=qc.selected(tenant,account,aweme,ids);
        if(selected.stream().anyMatch(p->p.stock()!=null&&p.stock()<=0))throw new BizException(1302,"已选商品库存不足，请重新选择商品");
        var scope=new Scope(tenant.value(),account,aweme);Bucket bucket;
        synchronized(cache){
            if(!cache.containsKey(scope)&&cache.size()>=MAX_SCOPES)cache.keySet().stream().findFirst().ifPresent(cache::remove);
            bucket=cache.computeIfAbsent(scope,key->new Bucket());
        }
        Pool pool;boolean hit;
        synchronized(bucket){
            var now=clock.instant();hit=!refresh&&bucket.pool!=null&&bucket.pool.fetchedAt().plusSeconds(60).isAfter(now);
            pool=hit?bucket.pool:scan(tenant,account,aweme);
            if(!hit&&pool.warning()==null)bucket.pool=pool;
        }
        // Never cache draft occupancy: another API/user can reserve a product between requests.
        var available=drafts.decorate(tenant.value(),account,draft,pool.products()).stream().filter(product->!excluded.contains(product.qcProductId())&&ProductRecommendationPolicy.eligible(product)).toList();
        var suggestions=combinations.recommend(tenant,account,aweme,selected,available,refresh);
        String message="AI根据组合售卖、用途互补和共同场景推荐；ROI接近仅依据既有计划目标，缺少数据不推断ROI。是否适合组合投放需实际测试。";
        if(pool.partial())message+=" 本次仅评估已取得的 "+pool.products().size()+" 个候选商品。";
        if(pool.warning()!=null)message+=" "+pool.warning();
        return new Result(account,aweme,List.copyOf(ids),suggestions,pool.products().size(),pool.total(),pool.partial(),hit,
                pool.fetchedAt().toString(),"AI_COMBINATION_V1",message);
    }
    private Pool scan(TenantId tenant,String account,String aweme){
        var products=new LinkedHashMap<String,Product>();long total=0;boolean partial=false;String warning=null;
        for(int page=1;page<=MAX_PAGES;page++){
            CreationQianchuanGateway.Page result;
            try{result=qc.recommendationCandidates(tenant,account,aweme,page,PAGE_SIZE);}
            catch(BizException error){
                if(page==1||Set.of(403,1201,1205,1211).contains(error.code()))throw error;
                partial=true;warning="部分候选商品暂未载入，可稍后重新匹配。";break;
            }
            total=Math.max(total,result.total());int before=products.size();
            result.items().forEach(p->products.putIfAbsent(p.qcProductId(),p));
            if(result.items().isEmpty()||products.size()==before){partial=products.size()<total;break;}
            if((long)page*PAGE_SIZE>=total)break;
            if(page==MAX_PAGES)partial=true;
        }
        return new Pool(List.copyOf(products.values()),total,partial,warning,clock.instant());
    }
}
