package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.application.ImportedPlanTransferService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ImportedPlanTransferTest {
    final TenantId tenant=new TenantId("tenant-a");final ObjectMapper json=new ObjectMapper();
    final AdPlanRepository plans=mock(AdPlanRepository.class);final PlanTransferStore store=mock(PlanTransferStore.class);final PlanRemoteStore remote=mock(PlanRemoteStore.class);
    final AdManagementPort management=mock(AdManagementPort.class);final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);final BillingTrialService billing=mock(BillingTrialService.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);final Credential credential=mock(Credential.class);
    AdPlan root,current;JsonNode rootDetail,currentDetail;ImportedPlanTransferService service;
    @BeforeEach void setup() throws Exception {
        when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        root=plan("root","111",SmartBidType.CONSERVATIVE,"aweme","account",PlanSource.LINGTOU,PlanStatus.PAUSED);
        current=plan("current","222",SmartBidType.CUSTOM,"unknown","account",PlanSource.EXTERNAL,PlanStatus.DELIVERING);
        when(plans.findById(tenant,root.id())).thenReturn(Optional.of(root));when(plans.findByTenant(tenant)).thenAnswer(i->List.of(root,current));
        when(credentials.getUsableCredential(tenant,root.accountId())).thenReturn(credential);
        rootDetail=json.readTree("{\"ad_id\":111,\"status\":\"DISABLE\",\"product_ids\":[101,102],\"delivery_setting\":{\"budget\":100,\"smart_bid_type\":\"SMART_BID_CONSERVATIVE\"}}");
        currentDetail=json.readTree("{\"ad_id\":222,\"status\":\"DELIVERY_OK\",\"product_ids\":[102,101],\"delivery_setting\":{\"budget\":300,\"roi2_goal\":1.5,\"smart_bid_type\":\"SMART_BID_CUSTOM\"}}");
        when(management.detail(credential,"111")).thenAnswer(i->rootDetail);when(management.detail(credential,"222")).thenAnswer(i->currentDetail);
        var cached=json.readTree("{\"product_info\":[{\"product_id\":101},{\"product_id\":102}],\"room_info\":[{\"anchor_id\":\"anchor\"}]}");
        when(remote.list(tenant)).thenReturn(Map.of("root",cache("root",cached),"current",cache("current",cached)));
        when(store.linkImported(any(),any())).thenReturn(new PlanTransferStore.Group("root","current","root","current",null));
        service=new ImportedPlanTransferService(plans,store,remote,management,credentials,billing,manager);
    }
    AdPlan plan(String id,String qc,SmartBidType mode,String aweme,String account,PlanSource source,PlanStatus status){return new AdPlan(new PlanId(id),tenant,new AccountId(account),new AwemeId(aweme),new PlanName("同名计划"),status,source,new DeliverySetting(mode,Money.ofYuan(100),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(new QcProductId("101"),new QcProductId("102")),new QcPlanId(qc),status.name(),null,1,null,List.of());}
    PlanRemoteStore.Remote cache(String id,JsonNode value){return new PlanRemoteStore.Remote(id,value,null,null,Instant.now(),null,null,null,null);}
    @Test void confirmedNativeTransferLinksAndRepairsBoundAwemeWithoutPlatformWrites(){
        assertEquals("root",service.associate(tenant,root.id(),"222").root());
        var saved=ArgumentCaptor.forClass(AdPlan.class);verify(plans).save(saved.capture());assertEquals(root.awemeId(),saved.getValue().awemeId());assertEquals(current.id(),saved.getValue().id());assertEquals(current.qcPlanId(),saved.getValue().qcPlanId());
        verify(management).detail(credential,"111");verify(management).detail(credential,"222");verifyNoMoreInteractions(management);
    }
    @Test void tenantCannotAssociateForeignPlan(){assertThrows(BizException.class,()->service.associate(new TenantId("other"),root.id(),"222"));verifyNoInteractions(management);verify(store,never()).linkImported(any(),any());}
    @Test void differentAccountCannotBeMergedEvenWithIdenticalNames(){current=plan("current","222",SmartBidType.CUSTOM,"unknown","other-account",PlanSource.EXTERNAL,PlanStatus.DELIVERING);assertThrows(BizException.class,()->service.associate(tenant,root.id(),"222"));verifyNoInteractions(management);}
    @Test void differentProductsCannotBeAssociated() throws Exception {currentDetail=json.readTree("{\"ad_id\":222,\"status\":\"DELIVERY_OK\",\"product_ids\":[999],\"delivery_setting\":{\"smart_bid_type\":\"SMART_BID_CUSTOM\"}}");assertThrows(BizException.class,()->service.associate(tenant,root.id(),"222"));verify(store,never()).linkImported(any(),any());}
    @Test void differentAnchorsCannotBeAssociated() throws Exception {when(remote.list(tenant)).thenReturn(Map.of("root",cache("root",json.readTree("{\"room_info\":[{\"anchor_id\":\"a\"}]}")),"current",cache("current",json.readTree("{\"room_info\":[{\"anchor_id\":\"b\"}]}"))));assertThrows(BizException.class,()->service.associate(tenant,root.id(),"222"));verify(store,never()).linkImported(any(),any());}
    @Test void twoEnabledPlansCannotBeMerged(){((com.fasterxml.jackson.databind.node.ObjectNode)rootDetail).put("status","DELIVERY_OK");assertThrows(BizException.class,()->service.associate(tenant,root.id(),"222"));verify(store,never()).linkImported(any(),any());}
    @Test void explicitDifferentAwemeCannotBeAssociated(){((com.fasterxml.jackson.databind.node.ObjectNode)rootDetail).put("aweme_id","aweme");((com.fasterxml.jackson.databind.node.ObjectNode)currentDetail).put("aweme_id","other");assertThrows(BizException.class,()->service.associate(tenant,root.id(),"222"));verify(store,never()).linkImported(any(),any());}
    @Test void oneExplicitAwemeStillMustMatchBoundAuthorization(){((com.fasterxml.jackson.databind.node.ObjectNode)currentDetail).put("aweme_id","other");assertThrows(BizException.class,()->service.associate(tenant,root.id(),"222"));verify(store,never()).linkImported(any(),any());}
    @Test void cacheCanSupplyProductsWhenDetailOmitsThem(){((com.fasterxml.jackson.databind.node.ObjectNode)rootDetail).remove("product_ids");((com.fasterxml.jackson.databind.node.ObjectNode)currentDetail).remove("product_ids");assertEquals("current",service.associate(tenant,root.id(),"222").active());}
}
