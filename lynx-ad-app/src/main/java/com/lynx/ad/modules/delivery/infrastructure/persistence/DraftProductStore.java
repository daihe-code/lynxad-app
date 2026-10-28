package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Product membership is serialized by the tenant-owned advertising account, across API instances. */
@Repository
public class DraftProductStore {
    private final JdbcTemplate jdbc;
    private DraftExpiryStore expiry;
    @org.springframework.beans.factory.annotation.Autowired public void draftExpiry(DraftExpiryStore value) { expiry=value; }
    public DraftProductStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public record Usage(String productId,String planId,String planName) {}

    public void assertAvailable(String tenant,String account,Collection<String> productIds,String currentPlan) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Draft product checks require the selection transaction");
        // Lock before reading or writing any plan. A JVM lock cannot protect the two production APIs.
        if(jdbc.query("SELECT id FROM oauth_account WHERE tenant_id=? AND id=? FOR UPDATE",
                (row,n)->row.getString(1),tenant,account).isEmpty())
            throw new BizException(1201,"广告账户不存在或不属于当前企业");
        var conflicts=usages(tenant,account,productIds,currentPlan,true);
        if(!conflicts.isEmpty()) {
            var conflict=conflicts.getFirst();
            throw new BizException(1404,"商品 "+conflict.productId()+" 已在草稿「"+conflict.planName()+"」（ID："+conflict.planId()+"）中，请继续编辑该草稿，或先从原草稿移除商品");
        }
    }

    public List<CreationState.Product> decorate(String tenant,String account,String currentPlan,List<CreationState.Product> products) {
        var byProduct=new LinkedHashMap<String,Usage>();
        usages(tenant,account,products.stream().map(CreationState.Product::qcProductId).toList(),currentPlan,false)
                .forEach(usage->byProduct.putIfAbsent(usage.productId(),usage));
        return products.stream().map(product->{
            var usage=byProduct.get(product.qcProductId());
            return product.withDraft(usage==null?null:new CreationState.DraftReference(usage.planId(),usage.planName()));
        }).toList();
    }

    private List<Usage> usages(String tenant,String account,Collection<String> productIds,String currentPlan,boolean lock) {
        if(expiry!=null)expiry.expireDue(tenant,java.time.Instant.now(),200);
        if(productIds==null||productIds.isEmpty())return List.of();
        var ids=productIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if(ids.isEmpty())return List.of();
        var params=new ArrayList<Object>(List.of(tenant,account));params.addAll(ids);
        String excluding=currentPlan==null||currentPlan.isBlank()?"":" AND p.id<>?";
        if(!excluding.isEmpty())params.add(currentPlan);
        // Locking reads also use current committed membership on MySQL's repeatable-read default.
        String sql="SELECT b.product_id,p.id,p.name FROM ad_plan p JOIN ad_plan_creative b ON b.ad_plan_id=p.id "
                +"WHERE p.tenant_id=? AND p.oauth_account_id=? AND p.status='DRAFT' AND p.qc_plan_id IS NULL "
                +"AND b.product_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")"+excluding
                +" ORDER BY p.id,b.product_id"+(lock?" FOR UPDATE":"");
        return jdbc.query(sql,(row,n)->new Usage(row.getString(1),row.getString(2),row.getString(3)),params.toArray());
    }
}
