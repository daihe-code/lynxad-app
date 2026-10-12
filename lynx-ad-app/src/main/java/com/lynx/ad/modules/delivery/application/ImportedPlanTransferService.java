package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Local association of a specifically confirmed Qianchuan transfer. Performs only remote GETs. */
@Service
public class ImportedPlanTransferService {
    private final AdPlanRepository plans;
    private final PlanTransferStore transfers;
    private final PlanRemoteStore remote;
    private final AdManagementPort management;
    private final QianchuanCredentialFacade credentials;
    private final BillingTrialService billing;
    private final TransactionTemplate tx;
    public ImportedPlanTransferService(AdPlanRepository plans,PlanTransferStore transfers,PlanRemoteStore remote,
                                       AdManagementPort management,QianchuanCredentialFacade credentials,BillingTrialService billing,PlatformTransactionManager manager) {
        this.plans=plans;this.transfers=transfers;this.remote=remote;this.management=management;this.credentials=credentials;this.billing=billing;tx=new TransactionTemplate(manager);
    }
    public PlanTransferStore.Group associate(TenantId tenant,PlanId rootId,String currentQcId) {
        synchronized(remote) {
            billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
            var root=plans.findById(tenant,rootId).orElseThrow(()->new BizException(1405,"原计划不存在"));
            var current=plans.findByTenant(tenant).stream().filter(p->p.qcPlanId().map(q->q.value().equals(currentQcId)).orElse(false)).findFirst()
                    .orElseThrow(()->new BizException(1405,"转投后的计划不存在或不属于当前账户"));
            if(root.qcPlanId().isEmpty() || root.id().equals(current.id()) || !root.accountId().equals(current.accountId()) || root.setting().bidType()==current.setting().bidType())
                throw new BizException(1402,"请选择同一广告账户下、投放方式不同的两套计划");
            transfers.assertIdle(tenant,rootId);transfers.assertIdle(tenant,current.id());
            var credential=credentials.getUsableCredential(tenant,root.accountId());
            var rootDetail=management.detail(credential,root.qcPlanId().orElseThrow().value());
            var currentDetail=management.detail(credential,currentQcId);
            if(status(rootDetail)!=PlanStatus.PAUSED || (status(currentDetail)!=PlanStatus.DELIVERING && status(currentDetail)!=PlanStatus.PENDING_AUDIT))
                throw new BizException(1404,"请先同步状态，原计划应已暂停，转投后的计划应已启用");
            if(!root.setting().bidType().oceanValue().equals(QianchuanPlanMapping.setting(rootDetail).path("smart_bid_type").asText()) || !current.setting().bidType().oceanValue().equals(QianchuanPlanMapping.setting(currentDetail).path("smart_bid_type").asText()))
                throw new BizException(1404,"平台投放方式已变化，请先同步计划");
            var cache=remote.list(tenant);var a=cache.get(root.id().value());var b=cache.get(current.id().value());
            Set<String> originalProducts=products(rootDetail,a),transferredProducts=products(currentDetail,b);
            if(originalProducts.isEmpty() || !originalProducts.equals(transferredProducts))throw new BizException(1402,"两套计划的推广商品不一致，不能关联为转投计划");
            String originalAweme=aweme(rootDetail),transferredAweme=aweme(currentDetail);
            if(!"unknown".equals(root.awemeId().value()) && ((originalAweme!=null && !originalAweme.equals(root.awemeId().value())) || (transferredAweme!=null && !transferredAweme.equals(root.awemeId().value()))))
                throw new BizException(1402,"平台计划的抖音号与原计划的授权信息不一致");
            if(originalAweme!=null && transferredAweme!=null) {
                if(!originalAweme.equals(transferredAweme) || (!"unknown".equals(root.awemeId().value())&&!originalAweme.equals(root.awemeId().value())))throw new BizException(1402,"两套计划的抖音号不一致");
            } else {
                Set<String> originalAnchors=anchors(a),transferredAnchors=anchors(b);
                if(originalAnchors.isEmpty() || !originalAnchors.equals(transferredAnchors))throw new BizException(1402,"未能确认两套计划使用同一抖音号，请先同步计划");
            }
            if("unknown".equals(root.awemeId().value()) || (!"unknown".equals(current.awemeId().value()) && !root.awemeId().equals(current.awemeId())))
                throw new BizException(1402,"计划的授权抖音号信息不完整或不一致");
            // List API provides anchor_id rather than the OAuth aweme_id. The confirmed pair inherits the root's bound ID.
            var repaired=new AdPlan(current.id(),tenant,current.accountId(),root.awemeId(),current.name(),status(currentDetail),current.source(),current.setting(),
                    current.productIds().isEmpty()?root.productIds():current.productIds(),current.qcPlanId().orElseThrow(),QianchuanPlanMapping.text(QianchuanPlanMapping.info(currentDetail),"status"),
                    current.rejectReason(),current.publishVersion(),current.publishKey().orElse(null),current.bindings());
            return tx.execute(s->{
                var group=transfers.linkImported(root,repaired);plans.save(repaired);
                remote.detail(tenant,root.id().value(),rootDetail);remote.detail(tenant,current.id().value(),currentDetail);return group;
            });
        }
    }
    private static PlanStatus status(JsonNode detail) {var info=QianchuanPlanMapping.info(detail);return "DISABLE".equals(info.path("opt_status").asText(detail.path("opt_status").asText()))?PlanStatus.PAUSED:QianchuanPlanMapping.status(info.path("status").asText());}
    private static String aweme(JsonNode detail) {String value=QianchuanPlanMapping.text(detail,"aweme_id");return value==null?QianchuanPlanMapping.text(QianchuanPlanMapping.info(detail),"aweme_id"):value;}
    private static Set<String> products(JsonNode detail,PlanRemoteStore.Remote cache) {
        Set<String> result=new HashSet<>();for(var id:detail.path("product_ids"))result.add(id.asText());
        for(var node:detail.path("multi_product_creative_list"))if(node.hasNonNull("product_id"))result.add(node.path("product_id").asText());
        if(result.isEmpty() && cache!=null)for(var node:cache.payload().path("product_info"))if(node.hasNonNull("product_id"))result.add(node.path("product_id").asText());
        return result;
    }
    private static Set<String> anchors(PlanRemoteStore.Remote cache) {
        Set<String> result=new HashSet<>();if(cache!=null)for(var node:cache.payload().path("room_info"))if(node.hasNonNull("anchor_id"))result.add(node.path("anchor_id").asText());return result;
    }
}
