package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanRemoteStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlanSyncService {
    public record SyncAccount(String accountId,int plans,List<String> warnings) {}
    public record SyncResult(List<SyncAccount> accounts,Instant completedAt) {}
    private final AdPlanRepository plans;
    private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;
    private final AdListPort list;
    private final AdManagementPort management;
    private final PlanRemoteStore store;
    private final TransactionTemplate transaction;
    private boolean live;
    private com.lynx.ad.modules.delivery.infrastructure.persistence.PlanOriginStore origins;
    @org.springframework.beans.factory.annotation.Autowired public void originStore(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanOriginStore value) { origins=value; }
    private com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore transfers;
    @org.springframework.beans.factory.annotation.Autowired public void transferStore(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore value) { transfers=value; }
    @org.springframework.beans.factory.annotation.Autowired
    public void configuration(com.lynx.ad.common.config.LynxAdProperties properties) {
        live=properties.getOauth().getQianchuan().isLive();
    }
    public PlanSyncService(AdPlanRepository plans,OauthAccountRepository accounts,QianchuanCredentialFacade credentials,
                           AdListPort list,AdManagementPort management,PlanRemoteStore store,PlatformTransactionManager tx) {
        this.plans=plans;this.accounts=accounts;this.credentials=credentials;this.list=list;this.management=management;this.store=store;
        this.transaction=new TransactionTemplate(tx);
    }
    /** Explicit sync; local pagination never repeats the remote account scan. No deletions on partial failure. */
    public SyncResult sync(TenantId tenant,String selectedAccount) {
        synchronized (store) { return syncLocked(tenant, selectedAccount); }
    }
    private SyncResult syncLocked(TenantId tenant,String selectedAccount) {
        if (selectedAccount != null && accounts.findById(tenant, new AccountId(selectedAccount)).isEmpty())
            throw new com.lynx.ad.common.exception.BizException(1201,"授权账户不存在或不属于当前灵投账户");
        List<SyncAccount> results=new ArrayList<>();
        for(var account:accounts.findByTenant(tenant)) {
            if(account.platform()!=com.lynx.ad.modules.identity.domain.model.Platform.QIANCHUAN || (live && account.grantId()==null)) continue;
            if(!"ACTIVE".equals(account.status()) || (selectedAccount!=null && !selectedAccount.equals(account.id().value()))) continue;
            List<String> warnings=new ArrayList<>(); int count=0;
            try {
                var credential=credentials.getUsableCredential(tenant,account.id());
                List<OceanPlanRow> rows=list.listIncludingDeleted(credential,null);
                var published=origins==null?Set.<String>of():origins.publishedIds(tenant,account.id(),rows.stream().map(OceanPlanRow::qcPlanId).toList());
                Map<String,AdPlan> local=new HashMap<>();
                for(var plan:plans.findByTenant(tenant)) if(plan.accountId().equals(account.id())) plan.qcPlanId().ifPresent(q->local.put(q.value(),plan));
                var pending=transfers==null?List.<com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore.Attempt>of():transfers.pending(tenant);
                Map<String,String> ids=new LinkedHashMap<>();
                for(var row:rows) {
                    if(row.payload()==null) continue;
                    var info=QianchuanPlanMapping.info(row.payload()); var setting=QianchuanPlanMapping.setting(row.payload());
                    AdPlan existing=local.get(row.qcPlanId());
                    if(existing!=null && existing.status()==PlanStatus.DELETED) continue;
                    String id=existing==null?store.findId(tenant,account.id(),row.qcPlanId()).orElseGet(()->UUID.randomUUID().toString()):existing.id().value();
                    if(existing==null)for(var attempt:pending) {
                        boolean sourceInAccount=local.values().stream().anyMatch(p->p.id().value().equals(attempt.source()));
                        if(sourceInAccount && row.qcPlanId().equals(attempt.qcId())) {id=attempt.target();break;}
                    }
                    final String physicalId=id;
                    var delivery=new DeliverySetting(row.bidType(),row.budget(),Optional.ofNullable(QianchuanPlanMapping.number(setting,"roi2_goal")).map(RoiGoal::new),
                            com.lynx.ad.modules.delivery.domain.service.PlanSchedule.read(setting,
                                    new Schedule(ScheduleType.FROM_NOW,QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting,"start_time")),QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting,"end_time")))),AudienceJson.empty());
                    if(existing==null) {
                        String aweme=QianchuanPlanMapping.text(row.payload(),"aweme_id");
                        if(aweme==null && row.payload().path("room_info").isArray() && !row.payload().path("room_info").isEmpty())
                            aweme=QianchuanPlanMapping.text(row.payload().path("room_info").get(0),"aweme_id");
                        existing=new AdPlan(new PlanId(id),tenant,account.id(),new AwemeId(aweme==null?"unknown":aweme),
                                PlanName.fromPlatform(row.name()),QianchuanPlanMapping.status(row.status()),PlanSource.EXTERNAL,delivery,List.of(),
                                new QcPlanId(row.qcPlanId()),row.status(),null,0,null,List.of());
                    } else {
                        existing.apply(QianchuanPlanMapping.status(row.status())); existing.syncPlatformStatus(row.status());
                        existing.rename(PlanName.fromPlatform(row.name())); existing.updateSetting(delivery);
                    }
                    if(published.contains(row.qcPlanId()))existing.restoreLingtouSource();
                    AdPlan plan=existing;
                    transaction.executeWithoutResult(s->{
                        plans.save(plan); store.save(tenant,account.id(),row.qcPlanId(),physicalId,row.payload());
                        if(transfers!=null && plan.status()==PlanStatus.DELETED) transfers.detachDeleted(tenant,plan.id());
                    });
                    ids.put(row.qcPlanId(),id); count++;
                }
                if(!ids.isEmpty()) {
                    try { management.guarantees(credential,new ArrayList<>(ids.keySet())).forEach((qc,status)->{
                        if(ids.containsKey(qc)) store.guarantee(tenant,ids.get(qc),status);
                    }); } catch(RuntimeException ex) { warnings.add("保障状态："+failure(ex)); }
                    try {
                        LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));
                        var today=reports(management.report(credential,day)); var yesterday=reports(management.report(credential,day.minusDays(1)));
                        Instant now=Instant.now(); ids.forEach((qc,id)->store.report(tenant,id,today.get(qc),yesterday.get(qc),day,now));
                    } catch(RuntimeException ex) { warnings.add("统计数据："+failure(ex)); }
                }
            } catch(RuntimeException ex) { warnings.add("计划同步："+failure(ex)); }
            results.add(new SyncAccount(account.id().value(),count,List.copyOf(warnings)));
        }
        if(transfers!=null)transfers.reconcileActive(tenant,plans.findByTenant(tenant));
        return new SyncResult(List.copyOf(results),Instant.now());
    }
    private String failure(RuntimeException ex) {
        return ex instanceof com.lynx.ad.common.exception.BizException ? ex.getMessage() : "本地保存或解析失败，请稍后重试";
    }
    private Map<String,JsonNode> reports(List<JsonNode> rows) {
        Map<String,JsonNode> result=new HashMap<>();
        for(JsonNode row:rows) {
            JsonNode id=row.path("dimensions").path("ad_id");
            if(id.isObject()) id=id.has("Value")?id.path("Value"):id.path("value");
            if(!id.isMissingNode() && !id.isNull()) result.put(id.asText(),row.path("metrics"));
        }
        return result;
    }
}
