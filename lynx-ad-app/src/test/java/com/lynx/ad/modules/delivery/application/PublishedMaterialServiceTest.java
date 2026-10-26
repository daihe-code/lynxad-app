package com.lynx.ad.modules.delivery.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class PublishedMaterialServiceTest {
    final ObjectMapper json=new ObjectMapper();final TenantId tenant=new TenantId("tenant");
    final Credential credential=new Credential("fake-token",new AdvertiserId("999"),"account");
    final AdPlanRepository plans=mock(AdPlanRepository.class);final PlanTransferStore transfers=mock(PlanTransferStore.class);
    final CreationQianchuanGateway gateway=mock(CreationQianchuanGateway.class);final AdManagementPort ads=mock(AdManagementPort.class);
    final CreationPayloadBuilder payloads=mock(CreationPayloadBuilder.class);
    final PlanRemoteStore metadata=mock(PlanRemoteStore.class);
    final Map<String,JsonNode> remote=new ConcurrentHashMap<>();
    CreationStore store;PublishedMaterialService service;String lock;boolean linked;DataSourceTransactionManager manager;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:published_material_"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        manager=new DataSourceTransactionManager(ds);try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V18__creation_workflow.sql"));}
        store=new CreationStore(new JdbcTemplate(ds),json,manager);service=new PublishedMaterialService(store,plans,transfers,gateway,ads,payloads,json,manager);
        service.remoteMetadata(metadata);
        var s=new CreationState();s.tenantId="tenant";s.planId="plan";s.accountId="account";s.awemeId="111";s.publishStatus="CREATED";s.generationStatus="SUCCEEDED";
        s.materials.add(material("old"));s.materials.add(material("keep"));store.insert(s);
        when(gateway.credential(tenant,"account","111")).thenReturn(credential);
        when(plans.findById(eq(tenant),any())).thenAnswer(i->Optional.of(plan(((PlanId)i.getArgument(1)).value(),"account",PlanStatus.PAUSED)));
        when(transfers.lockForProductChange(any(),any())).thenAnswer(i->{if(lock!=null)throw new BizException(1523,"已锁定");lock=i.getArgument(1);return group();});
        when(transfers.group(eq(tenant),any())).thenAnswer(i->group());
        doAnswer(i->{assertEquals(lock,i.getArgument(2));lock=null;return null;}).when(transfers).finishProductChange(any(),any(),any());
        for(String id:List.of("qc-plan","qc-cost"))remote.put(id,detail(id,"old","keep"));
        when(ads.detail(eq(credential),anyString())).thenAnswer(i->remote.get(i.getArgument(1)).deepCopy());
        when(payloads.optimizationMaterial(any(),any())).thenAnswer(i->visual(((CreationState.Material)i.getArgument(1)).id));
        when(ads.materialReport(eq(credential),anyString(),any(),any(),eq("VIDEO"))).thenAnswer(i->List.of(report("old","501"),report("keep","502")));
        doAnswer(i->{String qc=i.getArgument(1);var current=remote.get(qc);remote.put(qc,wrap(qc,PublishedMaterialService.without(current.path("multi_product_creative_list"),"123",Set.of("VIDEO:old"))));return null;}).when(ads).deleteMaterials(eq(credential),any(),any());
        doAnswer(i->{String qc=i.getArgument(1);remote.put(qc,wrap(qc,((JsonNode)i.getArgument(3)).path("creatives")));return null;}).when(ads).automaticContent(eq(credential),any(),eq("MATERIAL_ADD"),any());
    }
    PlanTransferStore.Group group(){return new PlanTransferStore.Group("plan","plan","plan",linked?"cost":null,lock);}
    AdPlan plan(String id,String account,PlanStatus status){return new AdPlan(new PlanId(id),tenant,new AccountId(account),new AwemeId("111"),new PlanName("计划"),status,PlanSource.LINGTOU,new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(),new QcPlanId("qc-"+id),"DISABLE",null,1,null,List.of());}
    CreationState.Material material(String id){var m=new CreationState.Material();m.id=id;m.type="VIDEO";m.productId="123";m.status="READY";return m;}
    ObjectNode visual(String id){var g=json.createObjectNode().put("product_id",123);g.putArray("video_material").addObject().put("video_id",id).put("video_cover_id","cover").put("image_mode","VERTICAL");return g;}
    ObjectNode detail(String id,String... visuals){var list=json.createArrayNode();var g=json.createObjectNode().put("product_id",123);for(String v:visuals)g.withArray("video_material").add(visual(v).path("video_material").get(0));g.putArray("title_material").addObject().put("title","原标题").put("title_type","CUSTOM");list.add(g);return wrap(id,list);}
    ObjectNode wrap(String id,JsonNode list){var d=json.createObjectNode().put("ad_id",id);d.set("multi_product_creative_list",list);return d;}
    ObjectNode report(String object,String material){var row=json.createObjectNode();row.putArray("product_info").addObject().put("product_id","123");row.putObject("material_info").putObject("video_material").put("video_id",object).put("material_id",material);return row;}
    CreationState begin(String action){return store.mutate("tenant","plan",null,s->{service.reserve(tenant,"plan",s,s.materials.getFirst(),action);if(action.equals("regenerate"))s.generationStatus="REVISING";s.changed();});}
    CreationState read(){return store.get("tenant","plan");}
    CreationState readyReplacement(){return store.mutate("tenant","plan",null,s->{var m=material("new");m.replacesId="old";s.materials.add(m);s.generationStatus="SUCCEEDED";});}
    void cooldown(){store.mutate("tenant","plan",null,s->s.materialEdit.nextCheckAt=0);}
    @Test void deleteConfirmsRemoteBeforeRemovingLocalVisualAndKeepsDeliverySettings(){var s=begin("delete");assertEquals(2,s.materials.size());var result=service.process(s);assertEquals("SUCCEEDED",result.materialEdit.status);assertEquals(List.of("keep"),result.materials.stream().map(m->m.id).toList());assertNull(lock);verify(ads).deleteMaterials(credential,"qc-plan",List.of("501"));verify(ads,never()).settings(any(),any(),any(),any(),any());}
    @Test void regenerationRetainsOriginalUntilNewVisualIsReadyAndBound(){var s=begin("regenerate");service.process(s);verify(ads,never()).automaticContent(any(),any(),any(),any());assertEquals(2,read().materials.size());s=readyReplacement();service.process(s);assertEquals(3,read().materials.size());var result=service.process(read());assertEquals("SUCCEEDED",result.materialEdit.status);assertEquals(List.of("keep","new"),result.materials.stream().map(m->m.id).toList());var order=inOrder(ads);order.verify(ads).automaticContent(eq(credential),eq("qc-plan"),eq("MATERIAL_ADD"),any());order.verify(ads).deleteMaterials(credential,"qc-plan",List.of("501"));assertTrue(PublishedMaterialService.objects(remote.get("qc-plan").path("multi_product_creative_list").get(0)).contains("VIDEO:new"));}
    @Test void linkedPlansBothConfirmBeforeOriginalIsRetired(){linked=true;var result=service.process(begin("delete"));assertEquals("SUCCEEDED",result.materialEdit.status);verify(ads).deleteMaterials(credential,"qc-plan",List.of("501"));verify(ads).deleteMaterials(credential,"qc-cost",List.of("501"));assertNull(lock);}
    @Test void deletingLastMaterialIsRejectedBeforeAnyPlatformWrite(){remote.put("qc-plan",detail("qc-plan","old"));assertThrows(BizException.class,()->begin("delete"));assertNull(read().materialEdit);verify(ads,never()).deleteMaterials(any(),any(),any());}
    @Test void changedAccountOrDeletedPlanIsRejected(){when(plans.findById(eq(tenant),any())).thenReturn(Optional.of(plan("plan","other",PlanStatus.PAUSED)));assertThrows(BizException.class,()->begin("delete"));verifyNoInteractions(ads);}
    @Test void timeoutIsReconciledWithoutReplayingDeletion(){doThrow(new BizException(1406,"timeout")).when(ads).deleteMaterials(any(),any(),any());service.process(begin("delete"));assertEquals("REMOVING",read().materialEdit.members.getFirst().phase);cooldown();service.process(read());verify(ads,times(1)).deleteMaterials(any(),any(),any());assertEquals(2,read().materials.size());remote.put("qc-plan",detail("qc-plan","keep"));cooldown();var result=service.process(read());assertEquals("SUCCEEDED",result.materialEdit.status);verify(ads,times(1)).deleteMaterials(any(),any(),any());assertNull(lock);}
    @Test void uncertainAdditionNeverRemovesOriginalAndNeverRepeatsModelOrPlatformWrite(){doThrow(new BizException(1406,"timeout")).when(ads).automaticContent(any(),any(),any(),any());begin("regenerate");service.process(readyReplacement());service.process(read());cooldown();service.process(read());verify(ads,times(1)).automaticContent(any(),any(),any(),any());verify(ads,never()).deleteMaterials(any(),any(),any());assertTrue(read().materials.stream().anyMatch(m->m.id.equals("old")));remote.put("qc-plan",wrap("qc-plan",read().materialEdit.members.getFirst().added));cooldown();assertEquals("SUCCEEDED",service.process(read()).materialEdit.status);}
    @Test void generationFailureUnlocksAndKeepsOriginal(){begin("regenerate");store.mutate("tenant","plan",null,s->s.generationStatus="FAILED");var result=service.process(read());assertEquals("FAILED",result.materialEdit.status);assertNull(lock);assertEquals(2,result.materials.size());verify(ads,never()).deleteMaterials(any(),any(),any());}
    @Test void generatedAlternativeUnlocksWithoutChangingPublishedBindings(){
        begin("regenerate");var ready=store.mutate("tenant","plan",null,s->{var m=material("new");m.reviewSourceId="old";m.versionDecision="PENDING";s.materials.add(m);s.generationStatus="SUCCEEDED";});
        var result=service.process(ready);assertEquals("SUCCEEDED",result.materialEdit.status);assertNull(lock);assertEquals(2,result.activeMaterials().size());assertEquals(3,result.materials.size());
        service.process(result);verify(ads,never()).automaticContent(any(),any(),any(),any());verify(ads,never()).deleteMaterials(any(),any(),any());
    }
    @Test void confirmedKeepBothAddsOnlyAndNeverDeletesOriginal(){
        var s=store.mutate("tenant","plan",null,v->{var next=material("new");next.reviewSourceId="old";next.versionDecision="APPLYING_KEEP_BOTH";v.materials.add(next);service.reserve(tenant,"plan",v,v.materials.getFirst(),"keep");v.materialEdit.versionId="new";for(var member:v.materialEdit.members)member.added=PublishedMaterialService.with(member.before,payloads.optimizationMaterial(v,next));});
        var result=service.process(s);assertEquals("SUCCEEDED",result.materialEdit.status);assertEquals(3,result.activeMaterials().size());assertEquals("KEEP_BOTH",result.materials.getLast().versionDecision);assertNull(lock);verify(ads,times(1)).automaticContent(any(),any(),eq("MATERIAL_ADD"),any());verify(ads,never()).deleteMaterials(any(),any(),any());
    }
    @Test void applicationVersionDecisionReplacesOnlyAfterExplicitConfirmationAndCanBeReplayed(){
        var s=store.mutate("tenant","plan",null,v->{var next=material("new");next.reviewSourceId="old";next.versionDecision="PENDING";v.materials.add(next);v.changed();});
        var workflow=new CreationWorkflowService(store,plans,null,gateway,mock(com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient.class),json,mock(com.lynx.ad.modules.creative.application.CreationAssetImporter.class),mock(com.lynx.ad.modules.creative.application.AssetUploadService.class),null,payloads,mock(com.lynx.ad.modules.billing.application.BillingTrialService.class),manager);
        workflow.publishedMaterials(service);
        var result=workflow.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(s.revision,"REPLACE"));
        assertEquals("REPLACE",result.materials.getLast().versionDecision);assertEquals(List.of("keep","new"),result.activeMaterials().stream().map(m->m.id).toList());
        workflow.resolveMaterialVersion(tenant,"plan","new",new CreationWorkflowService.VersionDecision(1,"REPLACE"));verify(ads,times(1)).automaticContent(any(),any(),any(),any());verify(ads,times(1)).deleteMaterials(any(),any(),any());
    }
    @Test void anotherOperationCannotStartWhileCurrentIntentIsPending(){begin("delete");assertThrows(BizException.class,()->begin("delete"));verify(ads,never()).deleteMaterials(any(),any(),any());}
    @Test void workerRestartUsesPersistedIntentAndAlreadyAppliedPlatformResult(){var s=begin("delete");remote.put("qc-plan",detail("qc-plan","keep"));var result=service.process(store.get("tenant","plan"));assertEquals("SUCCEEDED",result.materialEdit.status);verify(ads,never()).deleteMaterials(any(),any(),any());}
    @Test void concurrentForegroundAndWorkerCannotSubmitTwice()throws Exception{var s=begin("delete");var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->service.process(s));var b=pool.submit(()->service.process(s));a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);assertEquals("SUCCEEDED",read().materialEdit.status);verify(ads,times(1)).deleteMaterials(any(),any(),any());}finally{pool.shutdownNow();}}
    @Test void readbackToleratesOrderAndAbsentEmptyLists(){var a=detail("qc-plan","old","keep").path("multi_product_creative_list");var b=detail("qc-plan","keep","old").path("multi_product_creative_list");((ObjectNode)b.get(0)).putArray("image_material");assertTrue(PublishedMaterialService.same(a,b));}
    @Test void unrelatedRemoteChangesCannotBeOverwritten(){var s=begin("delete");remote.put("qc-plan",detail("qc-plan","old","keep","external"));var result=service.process(s);assertEquals("FAILED",result.materialEdit.status);assertEquals(2,result.materials.size());verify(ads,never()).deleteMaterials(any(),any(),any());assertNull(lock);}
    @Test void imageDeletionKeepsOtherImagesAndOtherProducts()throws Exception{var groups=json.readTree("[{\"product_id\":123,\"image_material\":[{\"image_mode\":\"SQUARE\",\"image_ids\":[\"old-image\",\"keep-image\"]}]},{\"product_id\":456,\"video_material\":[{\"video_id\":\"other\"}]}]");var result=PublishedMaterialService.without(groups,"123",Set.of("IMAGE:old-image"));assertEquals(Set.of("IMAGE:keep-image"),PublishedMaterialService.objects(result.get(0)));assertEquals(groups.get(1),result.get(1));assertEquals(2,groups.get(0).path("image_material").get(0).path("image_ids").size());}
    @Test void carouselReplacementAndReadbackKeepNativeCarousels()throws Exception{var groups=json.readTree("[{\"product_id\":123,\"carousel_material\":[{\"carousel_id\":101},{\"aweme_carousel_id\":102}]}]");var single=json.readTree("{\"product_id\":123,\"carousel_material\":[{\"carousel_id\":103}]}");var added=PublishedMaterialService.with(groups,single);assertEquals(3,PublishedMaterialService.objects(added.get(0)).size());var removed=PublishedMaterialService.without(added,"123",Set.of("CAROUSEL:101"));assertEquals(Set.of("CAROUSEL:102","CAROUSEL:103"),PublishedMaterialService.objects(removed.get(0)));assertEquals(2,PublishedMaterialService.objects(groups.get(0)).size());}
    @Test void ambiguousProductReportCannotDeleteAnotherProductsCreative(){var report=report("old","501");report.withArray("product_info").addObject().put("product_id","456");when(ads.materialReport(any(),any(),any(),any(),any())).thenReturn(List.of(report));assertThrows(BizException.class,()->begin("delete"));verify(ads,never()).deleteMaterials(any(),any(),any());assertNull(read().materialEdit);}
    @Test void anotherTenantsPublishedPlanCannotBeEdited(){assertThrows(BizException.class,()->service.requireEditor(new TenantId("other"),"plan",read()));verifyNoInteractions(ads);}
    @Test void planListAndMaterialMetadataRefreshOnlyAfterBothPlansConfirm(){linked=true;var result=service.process(begin("delete"));assertEquals("SUCCEEDED",result.materialEdit.status);for(String id:List.of("plan","cost"))verify(metadata).verifiedContent(eq(tenant),eq(id),argThat(detail->!PublishedMaterialService.objects(detail.path("multi_product_creative_list").get(0)).contains("VIDEO:old")),any());}
}
