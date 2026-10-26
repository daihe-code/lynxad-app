package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.application.PlanProductsService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PlanProductsTest {
    final TenantId tenant=new TenantId("tenant-a");final PlanId root=new PlanId("root"),cost=new PlanId("cost");final AccountId account=new AccountId("account");
    final ObjectMapper json=new ObjectMapper();final AdPlanRepository plans=mock(AdPlanRepository.class);final AdPlan original=mock(AdPlan.class),paired=mock(AdPlan.class);
    final OauthAccountRepository accounts=mock(OauthAccountRepository.class);final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);final AdManagementPort port=mock(AdManagementPort.class);
    final CreationStore creations=mock(CreationStore.class);final AdPlanCreativeBindingStore bindings=mock(AdPlanCreativeBindingStore.class);final PlanTransferStore transfers=mock(PlanTransferStore.class);
    final PlanProductChangeStore changes=mock(PlanProductChangeStore.class);final PlanRemoteStore remote=mock(PlanRemoteStore.class);final BillingTrialService billing=mock(BillingTrialService.class);final AuditRecorder audit=mock(AuditRecorder.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);final Map<String,Set<String>> nativeProducts=new LinkedHashMap<>();final Map<String,PlanProductChangeStore.Change> operations=new HashMap<>();
    final CreationState state=new CreationState();final Credential credential=new Credential("test-token",new AdvertiserId("123"));
    final String a="3841021345144111165",b="3841023363610968621",qcRoot="1878659667855539",qcCost="1878663839175696";
    final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));final PlanStatisticsRange range=new PlanStatisticsRange(today,today);
    PlanProductsService service;
    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        for(var p:List.of(original,paired)) {
            var id=p==original?root:cost;String qc=p==original?qcRoot:qcCost;
            when(p.id()).thenReturn(id);when(p.tenantId()).thenReturn(tenant);when(p.status()).thenReturn(PlanStatus.PAUSED);when(p.accountId()).thenReturn(account);when(p.awemeId()).thenReturn(new AwemeId("99"));when(p.qcPlanId()).thenReturn(Optional.of(new QcPlanId(qc)));
            when(plans.findById(tenant,id)).thenReturn(Optional.of(p));when(plans.findByIdForUpdate(tenant,id)).thenReturn(Optional.of(p));nativeProducts.put(qc,new LinkedHashSet<>(List.of(a,b)));
        }
        when(accounts.findById(tenant,account)).thenReturn(Optional.of(new OauthAccount(account,tenant,Platform.QIANCHUAN,new AdvertiserId("123"),"账户",null,null,Instant.now().plusSeconds(3600),null,"ACTIVE",List.of())));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);
        var group=new PlanTransferStore.Group("root","cost","root","cost",null);
        when(transfers.group(eq(tenant),any())).thenReturn(group);when(transfers.lockForProductChange(eq(original),anyString())).thenReturn(group);when(transfers.lockForProductChange(eq(paired),anyString())).thenReturn(group);
        state.products.add(new CreationState.Product("local-a",a,"棉鞋",null,null,"image-a",null,null,List.of(),List.of()));state.products.add(new CreationState.Product("local-b",b,"手套",null,null,"image-b",null,null,List.of(),List.of()));
        for(String p:List.of("local-a","local-b")){var material=new CreationState.Material();material.id="media-"+p;material.productId=p;material.status="READY";state.materials.add(material);}
        when(creations.get(tenant.value(),"root")).thenReturn(state);
        when(creations.mutate(eq(tenant.value()),eq("root"),isNull(),any())).thenAnswer(i->{i.<Consumer<CreationState>>getArgument(3).accept(state);return state;});
        when(port.detail(eq(credential),anyString())).thenAnswer(i->detail(i.getArgument(1)));
        when(port.products(eq(credential),anyString(),any(),any())).thenReturn(List.of());
        doAnswer(i->{nativeProducts.get(i.<String>getArgument(1)).remove(i.<String>getArgument(2));return null;}).when(port).deleteProduct(eq(credential),anyString(),anyString());
        when(changes.get(eq(tenant),anyString())).thenAnswer(i->Optional.ofNullable(operations.get(i.<String>getArgument(1))));
        doAnswer(i->{var c=i.<PlanProductChangeStore.Change>getArgument(0);operations.put(c.key(),c);return null;}).when(changes).insert(any());
        when(changes.claim(any())).thenReturn(true);
        doAnswer(i->{var c=i.<PlanProductChangeStore.Change>getArgument(0);operations.put(c.key(),new PlanProductChangeStore.Change(c.tenant(),c.key(),c.root(),c.source(),c.product(),c.members(),"DONE",null));return null;}).when(changes).complete(any());
        service=new PlanProductsService(plans,accounts,credentials,port,creations,bindings,transfers,changes,remote,billing,audit,json,manager);
    }
    JsonNode detail(String qc) {var row=json.createObjectNode().put("ad_id",qc);var list=row.putArray("multi_product_creative_list");nativeProducts.get(qc).forEach(p->list.addObject().put("product_id",p));return row;}
    JsonNode stats(String p,int show,int click,int spend) {var row=json.createObjectNode().put("product_id",p).put("is_del",false);row.putObject("product_info").put("product_id",p).put("product_name",p.equals(a)?"原生棉鞋":"原生手套").put("audit_status","PASS");row.putObject("stats_info").put("product_show_count_for_roi2",show).put("product_click_count_for_roi2",click).put("stat_cost_for_roi2",spend).put("product_cvr_rate_for_roi2",6.45);return row;}
    PlanProductsService.DeleteRequest request(String qc,String key) {return new PlanProductsService.DeleteRequest(key,qc);}
    @Test void intervalRowsUseSelectedPhysicalIdAndMergeGoodsWithoutConsumption() {
        when(port.products(credential,qcRoot,today,today)).thenReturn(List.of(stats(a,31,2,3)));
        var result=service.products(tenant,root,range);assertEquals(2,result.records().size());assertEquals("原生棉鞋",result.records().getFirst().name());assertEquals(new BigDecimal("6.45"),result.records().getFirst().metrics().get("ctr"));assertNull(result.records().getLast().metrics().get("cost"));assertEquals(1,result.dataProductCount());assertTrue(result.shared());verify(port).products(credential,qcRoot,today,today);verify(port,never()).products(eq(credential),eq(qcCost),any(),any());
    }
    @Test void bothTransferModesGetSharedCurrentPricesWithoutSharingReportMetrics() {
        var prices=mock(com.lynx.ad.modules.catalog.application.ProductPriceService.class);
        var price=new com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice(new BigDecimal("17.89"),new BigDecimal("19.89"),null,"QIANCHUAN_AVAILABLE",java.time.Instant.now(),false);
        when(prices.prices(eq(tenant),eq(credential),anyString(),any(),anyBoolean())).thenReturn(Map.of(a,price));service.productPrices(prices);
        when(port.products(credential,qcRoot,today,today)).thenReturn(List.of(stats(a,31,2,3)));
        var volume=service.products(tenant,root,range);var custom=service.products(tenant,cost,range,true);
        assertEquals(price,volume.records().getFirst().priceInfo());assertEquals(price,custom.records().getFirst().priceInfo());
        assertEquals(new BigDecimal("3"),volume.records().getFirst().metrics().get("cost"));assertNull(custom.records().getFirst().metrics().get("cost"));
        verify(prices).prices(eq(tenant),eq(credential),anyString(),any(),eq(true));
    }
    @Test void pausedRealZeroSurvivesAndMissingMetricsAreUnknown() {when(port.products(credential,qcCost,today,today)).thenReturn(List.of(stats(a,0,0,0)));var result=service.products(tenant,cost,range);assertEquals(BigDecimal.ZERO,result.records().getFirst().metrics().get("cost"));assertNull(result.records().getFirst().metrics().get("orders"));}
    @Test void nativeReportFailureKeepsGoodsAndMakesWarningExplicit() {when(port.products(eq(credential),eq(qcRoot),any(),any())).thenThrow(new BizException(1406,"权限不足"));var r=service.products(tenant,root,range);assertEquals(2,r.records().size());assertTrue(r.warning().contains("权限不足"));assertEquals(0,r.dataProductCount());}
    @Test void deletedOrForeignHistoricalProductCannotLeakIntoCurrentGoods() {var deleted=stats(a,31,2,3);((com.fasterxml.jackson.databind.node.ObjectNode)deleted).put("is_del",true);when(port.products(credential,qcRoot,today,today)).thenReturn(List.of(deleted,stats("1234",20,5,7)));var r=service.products(tenant,root,range);assertEquals(0,r.dataProductCount());assertEquals(2,r.records().size());}
    @Test void wrongNativePlanIdBlocksReadAndDeletion() {when(port.detail(credential,qcRoot)).thenReturn(detail(qcCost));assertThrows(BizException.class,()->service.products(tenant,root,range));assertThrows(BizException.class,()->service.delete(tenant,root,a,request(qcRoot,UUID.randomUUID().toString())));verify(port,never()).deleteProduct(any(),any(),any());}
    @Test void otherTenantAndDeletedPlanCannotReadGoods() {assertThrows(BizException.class,()->service.products(new TenantId("other"),root,range));when(original.status()).thenReturn(PlanStatus.DELETED);assertThrows(BizException.class,()->service.products(tenant,root,range));verifyNoInteractions(port);}
    @Test void deletionSynchronizesBothPlansAndCleansOnlySelectedBindingsAndSharedMaterialReferences() {String key=UUID.randomUUID().toString();assertEquals("DONE",service.delete(tenant,cost,a,request(qcCost,key)).status());for(String qc:List.of(qcRoot,qcCost))assertEquals(Set.of(b),nativeProducts.get(qc));verify(bindings).removeProduct(root,a);verify(bindings).removeProduct(cost,a);verify(bindings,never()).upsert(any(),any());assertEquals(List.of(b),state.products.stream().map(CreationState.Product::qcProductId).toList());assertEquals("media-local-b",state.materials.getFirst().id);assertTrue(state.removedMaterialIds.contains("media-local-a"));verify(transfers).finishProductChange(tenant,"root",key);}
    @Test void lastProductInEitherMemberRejectsBeforeAnyNativeDeletion() {nativeProducts.put(qcCost,new LinkedHashSet<>(List.of(a)));assertThrows(BizException.class,()->service.delete(tenant,root,a,request(qcRoot,UUID.randomUUID().toString())));verify(port,never()).deleteProduct(any(),any(),any());verify(changes,never()).insert(any());}
    @Test void secondMemberFailureKeepsDurablePendingAndRetryReadsBeforeSending() {String key=UUID.randomUUID().toString();doThrow(new BizException(1406,"接口超时")).when(port).deleteProduct(credential,qcCost,a);assertEquals("PENDING",service.delete(tenant,root,a,request(qcRoot,key)).status());verify(bindings,never()).removeProduct(any(),any());verify(transfers,never()).finishProductChange(any(),any(),any());doAnswer(i->{nativeProducts.get(qcCost).remove(a);return null;}).when(port).deleteProduct(credential,qcCost,a);assertEquals("DONE",service.delete(tenant,root,a,request(qcRoot,key)).status());verify(port,times(1)).deleteProduct(credential,qcRoot,a);verify(port,times(2)).deleteProduct(credential,qcCost,a);}
    @Test void timeoutAfterDeletionIsReadBackOnRetryWithoutResending() {String key=UUID.randomUUID().toString();doAnswer(i->{nativeProducts.get(qcRoot).remove(a);throw new BizException(1406,"连接中断");}).when(port).deleteProduct(credential,qcRoot,a);assertEquals("PENDING",service.delete(tenant,root,a,request(qcRoot,key)).status());assertEquals("DONE",service.delete(tenant,cost,a,request(qcCost,key)).status());verify(port,times(1)).deleteProduct(credential,qcRoot,a);}
    @Test void acknowledgementWithoutRemovalIsNeverSuccess() {doNothing().when(port).deleteProduct(credential,qcRoot,a);assertEquals("PENDING",service.delete(tenant,root,a,request(qcRoot,UUID.randomUUID().toString())).status());verify(bindings,never()).removeProduct(any(),any());}
    @Test void completedRequestDoesNotDeleteAgainAndCannotBeUsedForAnotherProduct() {String key=UUID.randomUUID().toString();service.delete(tenant,root,a,request(qcRoot,key));clearInvocations(port);assertEquals("DONE",service.delete(tenant,root,a,request(qcRoot,key)).status());verifyNoInteractions(port);assertThrows(BizException.class,()->service.delete(tenant,root,b,request(qcRoot,key)));}
    @Test void changedQcIdAndCrossAccountPairAreRejected() {assertThrows(BizException.class,()->service.delete(tenant,root,a,request("999",UUID.randomUUID().toString())));when(paired.accountId()).thenReturn(new AccountId("other"));assertThrows(BizException.class,()->service.delete(tenant,root,a,request(qcRoot,UUID.randomUUID().toString())));verify(port,never()).deleteProduct(any(),any(),any());}
    @Test void concurrentWorkerCannotMutateTheSameOperation() {when(changes.claim(any())).thenReturn(false);assertEquals("PENDING",service.delete(tenant,root,a,request(qcRoot,UUID.randomUUID().toString())).status());verify(port,never()).deleteProduct(any(),any(),any());}
    @Test void activeGenerationPreventsDeletionAndLeavesMaterials() {state.generationStatus="RUNNING";assertThrows(BizException.class,()->service.delete(tenant,root,a,request(qcRoot,UUID.randomUUID().toString())));assertEquals(2,state.materials.size());verifyNoInteractions(port);}
    @Test void generatingWorkingDraftAlsoLocksThePublishedPlansProductDeleteEntry() {
        state.publishedEditDraftId="working";var work=new CreationState();work.generationStatus="RUNNING";
        when(creations.get(tenant.value(),"working")).thenReturn(work);
        var result=service.products(tenant,root,range);
        assertTrue(result.records().stream().noneMatch(PlanProductsService.Product::deletable));assertTrue(result.warning().contains("暂不能调整商品"));
        clearInvocations(port);
        assertThrows(BizException.class,()->service.delete(tenant,root,a,request(qcRoot,UUID.randomUUID().toString())));
        verifyNoInteractions(port);assertEquals(2,state.materials.size());
    }
    @Test void failedLocalCommitRemainsRecoverableAfterAllNativeDeletes() {String key=UUID.randomUUID().toString();doThrow(new RuntimeException("db-failure")).when(bindings).removeProduct(root,a);assertEquals("PENDING",service.delete(tenant,root,a,request(qcRoot,key)).status());doNothing().when(bindings).removeProduct(root,a);assertEquals("DONE",service.delete(tenant,root,a,request(qcRoot,key)).status());verify(port,times(1)).deleteProduct(credential,qcRoot,a);verify(port,times(1)).deleteProduct(credential,qcCost,a);}
    @Test void completedConcurrentRequestObservedAfterGroupLockDoesNotLeaveGroupLocked() {String key=UUID.randomUUID().toString();var completed=new PlanProductChangeStore.Change(tenant.value(),key,"root","root",a,"[{\"id\":\"root\",\"qcId\":\""+qcRoot+"\"}]","DONE",null);when(changes.get(tenant,key)).thenReturn(Optional.empty(),Optional.of(completed));assertEquals("DONE",service.delete(tenant,root,a,request(qcRoot,key)).status());verify(transfers).finishProductChange(tenant,"root",key);verify(changes,never()).insert(any());verifyNoInteractions(port);}
    @Test void supplementalHistoricalMetadataDoesNotRestoreDeletedCreativeMembership() {var d=(com.fasterxml.jackson.databind.node.ObjectNode)detail(qcRoot);d.putArray("product_infos").addObject().put("product_id","1234");when(port.detail(credential,qcRoot)).thenReturn(d);assertEquals(2,service.products(tenant,root,range).records().size());}
}
