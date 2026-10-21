package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.application.PlanQueryService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.AdPlanCreativeBindingStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanStatusFilterTest {
    final TenantId tenant=new TenantId("tenant");
    final AccountId account=new AccountId("account");
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    final OauthAccountRepository accounts=mock(OauthAccountRepository.class);
    final AdListPort platform=mock(AdListPort.class);
    final PlanQueryService query=new PlanQueryService(plans,platform,accounts,mock(QianchuanCredentialFacade.class),
            mock(com.lynx.ad.modules.dataops.domain.port.MetricSnapshotRepository.class),
            mock(com.lynx.ad.modules.catalog.domain.port.ProductRepository.class),mock(AdPlanCreativeBindingStore.class));
    final List<AdPlan> fixtures=new ArrayList<>();
    @BeforeEach void prepare() {
        when(accounts.findByTenant(tenant)).thenReturn(List.of(new OauthAccount(account,tenant,Platform.QIANCHUAN,new AdvertiserId("123"),"账户",null,null,Instant.now().plusSeconds(3600),null,"ACTIVE",List.of())));
        for(String raw:List.of("DELIVERY_OK","AUDIT","REAUDIT","OFFLINE_AUDIT","DISABLE","OFFLINE_BUDGET","ADVERTISER_OFFLINE_BUDGET","OFFLINE_BALANCE","SYSTEM_DISABLE","NO_SCHEDULE","TIME_NO_REACH","QUOTA_DISABLE","EXTERNAL_URL_DISABLE","TIME_DONE","FROZEN","DELETE"))
            fixtures.add(plan(raw,raw,QianchuanPlanMapping.status(raw)));
        when(plans.findByTenant(tenant)).thenReturn(fixtures);
    }
    AdPlan plan(String id,String raw,PlanStatus status) {
        return new AdPlan(new PlanId(id),tenant,account,new AwemeId("aweme"),new PlanName("测试-"+id),status,PlanSource.EXTERNAL,
                new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(),new QcPlanId(id),raw,null,0,null,List.of());
    }
    PlanQueryService.Filters filter(String status){return new PlanQueryService.Filters(null,status,null,null,null,null,null,false);}
    @Test void pausedParentIncludesNineReasonsWhileChildrenMatchExactly() {
        var page=query.list(tenant,1,200,filter("PAUSED"));assertEquals(9,page.total());assertEquals(9,page.summary().plans());
        for(String code:List.of("DISABLE","OFFLINE_BUDGET","ADVERTISER_OFFLINE_BUDGET","OFFLINE_BALANCE","SYSTEM_DISABLE","NO_SCHEDULE","TIME_NO_REACH","QUOTA_DISABLE","EXTERNAL_URL_DISABLE")) {
            var selected=query.list(tenant,1,200,filter(code));assertEquals(1,selected.total(),code);assertEquals(code,selected.records().getFirst().qcStatus());
        }
        var manual=page.records().stream().filter(row->"DISABLE".equals(row.qcStatus())).findFirst().orElseThrow();assertTrue(manual.canEdit());
        var system=page.records().stream().filter(row->"SYSTEM_DISABLE".equals(row.qcStatus())).findFirst().orElseThrow();
        assertEquals("UNKNOWN",system.status());assertFalse(system.canEdit());assertFalse(system.canControl());assertFalse(system.canTransfer());verifyNoInteractions(platform);
    }
    @Test void auditsAndEndStatesStaySeparateWithoutInventingMissingSubtypes() {
        assertEquals("AUDIT",query.list(tenant,1,20,filter("AUDIT")).records().getFirst().qcStatus());
        assertEquals("REAUDIT",query.list(tenant,1,20,filter("REAUDIT")).records().getFirst().qcStatus());
        assertEquals(2,query.list(tenant,1,20,filter("PENDING_AUDIT")).total());
        assertEquals("TIME_DONE",query.list(tenant,1,20,filter("COMPLETED")).records().getFirst().qcStatus());
        assertEquals("FROZEN",query.list(tenant,1,20,filter("TERMINATED")).records().getFirst().qcStatus());
        fixtures.add(plan("legacy",null,PlanStatus.FINISHED));
        assertEquals(1,query.list(tenant,1,20,filter("COMPLETED")).total());assertEquals(1,query.list(tenant,1,20,filter("TERMINATED")).total());
        assertEquals(3,query.list(tenant,1,20,filter("FINISHED")).total());
    }
    @Test void deletedRecordsAreOptInPaginatedAndCannotOpenRemovedDetailEndpoints() {
        assertEquals(15,query.list(tenant,1,200).total());assertEquals(15,query.list(tenant,1,200,filter("ALL")).total());
        var deleted=query.list(tenant,1,20,filter("DELETED"));assertEquals(1,deleted.total());assertEquals("DELETE",deleted.records().getFirst().qcStatus());
        assertFalse(deleted.records().getFirst().canEdit());assertFalse(deleted.records().getFirst().canControl());assertFalse(deleted.records().getFirst().canTransfer());
        var first=query.list(tenant,1,10,filter("ALL_INCLUDE_DELETED"));var second=query.list(tenant,2,10,filter("ALL_INCLUDE_DELETED"));
        assertEquals(16,first.total());assertEquals(10,first.records().size());assertEquals(6,second.records().size());assertEquals(16,first.summary().plans());
        var removed=fixtures.stream().filter(plan->plan.status()==PlanStatus.DELETED).findFirst().orElseThrow();when(plans.findById(tenant,removed.id())).thenReturn(Optional.of(removed));
        assertEquals(1405,assertThrows(BizException.class,()->query.get(tenant,removed.id())).code());assertEquals(PlanStatus.DELETED,removed.status());
    }
    @Test void invalidStatusFailsBeforeAnyAccountOrPlanRead() {
        clearInvocations(plans,accounts);
        assertEquals(1402,assertThrows(BizException.class,()->query.list(tenant,1,20,filter("invalid"))).code());verifyNoInteractions(plans,accounts,platform);
    }
}
