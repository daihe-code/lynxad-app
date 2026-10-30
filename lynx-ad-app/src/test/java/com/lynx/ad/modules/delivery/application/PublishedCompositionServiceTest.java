package com.lynx.ad.modules.delivery.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.domain.service.CreationProductSelection;
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

class PublishedCompositionServiceTest {
    final ObjectMapper json=new ObjectMapper();final TenantId tenant=new TenantId("tenant");
    final AdPlanRepository plans=mock(AdPlanRepository.class);final PlanTransferStore transfers=mock(PlanTransferStore.class);
    final CreationQianchuanGateway gateway=mock(CreationQianchuanGateway.class);final AdManagementPort ads=mock(AdManagementPort.class);
    final CreationPayloadBuilder payloads=mock(CreationPayloadBuilder.class);final PlanRemoteStore metadata=mock(PlanRemoteStore.class);
    final AdPlanCreativeBindingStore bindings=mock(AdPlanCreativeBindingStore.class);final BillingTrialService billing=mock(BillingTrialService.class);
    final Credential credential=new Credential("fake",new AdvertiserId("999"),"account");
    final Map<String,JsonNode> remote=new ConcurrentHashMap<>();String lock;boolean linked;
    CreationStore store;PublishedCompositionService service;
    @BeforeEach void setup()throws Exception{
        var ds=new DriverManagerDataSource("jdbc:h2:mem:composition_"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");var manager=new DataSourceTransactionManager(ds);
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V18__creation_workflow.sql"));}
        store=new CreationStore(new JdbcTemplate(ds),json,manager);service=new PublishedCompositionService(store,plans,transfers,gateway,ads,payloads,bindings,metadata,billing,json,manager);
        var source=new CreationState();source.tenantId="tenant";source.userId="user";source.planId="plan";source.accountId="account";source.awemeId="111";source.name="计划";source.publishStatus="CREATED";source.qcPlanId="1001";source.generationStatus="SUCCEEDED";source.enginePlanId="original-engine-task";source.budget=java.math.BigDecimal.valueOf(300);
        source.products.add(product("123"));source.materials.add(material("123"));store.insert(source);
        when(gateway.credential(tenant,"account","111")).thenReturn(credential);
        when(plans.findById(eq(tenant),any())).thenAnswer(i->Optional.of(plan(((PlanId)i.getArgument(1)).value())));
        when(plans.findByIdForUpdate(eq(tenant),any())).thenAnswer(i->Optional.of(plan(((PlanId)i.getArgument(1)).value())));
        when(transfers.group(eq(tenant),any())).thenAnswer(i->group());
        when(transfers.lockForProductChange(any(),any())).thenAnswer(i->{String key=i.getArgument(1);if(lock!=null&&!key.equals(lock))throw new BizException(1523,"locked");String prior=lock;lock=key;org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCompletion(int status){if(status!=STATUS_COMMITTED)lock=prior;}});return group();});
        doAnswer(i->{assertEquals(lock,i.getArgument(2));lock=null;return null;}).when(transfers).finishProductChange(any(),any(),any());
        when(payloads.creatives(any())).thenAnswer(i->creatives(i.getArgument(0)));
        remote.put("1001",wrap("1001",creatives(source)));remote.put("1002",wrap("1002",creatives(source)));
        when(ads.detail(eq(credential),anyString())).thenAnswer(i->remote.get(i.getArgument(1)).deepCopy());
        doAnswer(i->{String id=i.getArgument(1);JsonNode groups=((JsonNode)i.getArgument(3)).path("creatives");assertTrue(groups.size()<=10);assertFalse(groups.isEmpty());remote.put(id,wrap(id,groups));return null;}).when(ads).automaticContent(eq(credential),any(),eq("MATERIAL_ADD"),any());
        doAnswer(i->{String id=i.getArgument(1),product=i.getArgument(2);var list=json.createArrayNode();for(var g:remote.get(id).path("multi_product_creative_list"))if(!product.equals(g.path("product_id").asText()))list.add(g);assertFalse(list.isEmpty());remote.put(id,wrap(id,list));return null;}).when(ads).deleteProduct(eq(credential),any(),any());
    }
    PlanTransferStore.Group group(){return new PlanTransferStore.Group("plan","plan","plan",linked?"cost":null,lock);}
    AdPlan plan(String id){return new AdPlan(new PlanId(id),tenant,new AccountId("account"),new AwemeId("111"),new PlanName("计划"),PlanStatus.PAUSED,PlanSource.LINGTOU,new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(new QcProductId("123")),new QcPlanId(id.equals("plan")?"1001":"1002"),"DISABLE",null,1,null,List.of());}
    CreationState.Product product(String id){return new CreationState.Product(id,id,"商品"+id,java.math.BigDecimal.TEN,null,"https://assets.test/"+id,"类目",10L,List.of(),List.of());}
    CreationState.Material material(String id){var m=new CreationState.Material();m.id="material-"+id;m.productId=id;m.type="VIDEO";m.status="READY";m.videoAssetId="video-"+id;m.engineJobId="original-job";m.groupId="group-1";return m;}
    ArrayNode creatives(CreationState s){var groups=json.createArrayNode();for(var p:s.products){var g=groups.addObject().put("product_id",Long.parseLong(p.qcProductId())).put("hide_in_aweme",true);g.putArray("title_material").addObject().put("title","标题").put("title_type","CUSTOM");g.putObject("creative_card").put("promotion_card_image_id","cover");for(var m:s.materials)if(m.productId.equals(p.qcProductId()))g.withArray("video_material").addObject().put("video_id",m.videoAssetId).put("video_cover_id","cover").put("image_mode","VIDEO_VERTICAL");}return groups;}
    ObjectNode wrap(String id,JsonNode groups){var d=json.createObjectNode().put("ad_id",id);d.set("multi_product_creative_list",groups.deepCopy());return d;}
    CreationState read(String id){return store.get("tenant",id);}
    @Test void addingVideoToPausedPlanDoesNotRecheckItsExistingProductAsANewPlan() {
        doThrow(new BizException(1302,"商品正在使用商品全域推广，暂不支持创建"))
                .when(gateway).selected(tenant,"account","111",List.of("123"));
        var work=service.begin(tenant,"plan");
        work=store.mutate("tenant",work.planId,work.revision,s->{
            var video=material("123");video.id="new-video";video.videoAssetId="new-uploaded-video";s.materials.add(video);
        });
        assertEquals("SUCCEEDED",service.save(tenant,work.planId,work.revision).publishedEdit.status);
        assertEquals("1001",read("plan").qcPlanId);assertEquals(2,read("plan").materials.size());
        assertEquals(List.of("123"),read("plan").products.stream().map(CreationState.Product::qcProductId).toList());
        assertEquals(PlanStatus.PAUSED,plan("plan").status());
        verify(gateway,never()).selected(any(),any(),any(),any());
        verify(ads).automaticContent(eq(credential),eq("1001"),eq("MATERIAL_ADD"),any());
        verify(ads,never()).deleteProduct(any(),any(),any());verify(plans,never()).save(any());
    }
    @Test void addingAProductValidatesOnlyTheNewProductAndPreservesTheBoundProduct() {
        var work=adjust(service.begin(tenant,"plan"),"123","456");
        assertEquals("SUCCEEDED",service.save(tenant,work.planId,work.revision).publishedEdit.status);
        verify(gateway).selected(tenant,"account","111",List.of("456"));
        verify(gateway,never()).selected(tenant,"account","111",List.of("123","456"));
        assertEquals(List.of("123","456"),read("plan").products.stream().map(CreationState.Product::qcProductId).toList());
    }
    @Test void genuinelyUnavailableNewProductIsRejectedBeforeAnyPlatformWrite() {
        var work=adjust(service.begin(tenant,"plan"),"456");
        doThrow(new BizException(1302,"商品不可投：新商品已下架")).when(gateway).selected(tenant,"account","111",List.of("456"));
        assertThrows(BizException.class,()->service.save(tenant,work.planId,work.revision));
        assertEquals("DRAFT",read(work.planId).publishedEdit.status);assertNull(lock);
        assertEquals("123",read("plan").products.getFirst().qcProductId());
        verify(ads,never()).automaticContent(any(),any(),any(),any());verify(ads,never()).deleteProduct(any(),any(),any());
    }
    @Test void removingAnExistingProductDoesNotRequireCreationEligibilityForTheRetainedOne() {
        var source=store.mutate("tenant","plan",null,s->{s.products.add(product("456"));s.materials.add(material("456"));});
        remote.put("1001",wrap("1001",creatives(source)));
        var work=adjust(service.begin(tenant,"plan"),"456");
        assertEquals("SUCCEEDED",service.save(tenant,work.planId,work.revision).publishedEdit.status);
        verify(gateway,never()).selected(any(),any(),any(),any());verify(ads).deleteProduct(credential,"1001","123");
    }
    @Test void invalidSavedProductMembershipIsStillRejectedBeforeAnyWrite() {
        var work=service.begin(tenant,"plan");
        work=store.mutate("tenant",work.planId,work.revision,s->s.products.add(product("123")));
        var invalid=work;
        assertThrows(BizException.class,()->service.save(tenant,invalid.planId,invalid.revision));
        assertNull(lock);verify(ads,never()).automaticContent(any(),any(),any(),any());
        verify(gateway,never()).selected(any(),any(),any(),any());
    }
    @Test void beginContentEditAcceptsUploadedVideoWithUnusedAwemeId() {
        ((ObjectNode)remote.get("1001").path("multi_product_creative_list").get(0)
                .path("video_material").get(0)).put("aweme_item_id",0);
        var work=service.begin(tenant,"plan");
        assertEquals("DRAFT",work.publishedEdit.status);
        assertEquals("NONE",work.publishStatus);
        assertEquals("video-123",work.publishedEdit.members.getFirst().before.get(0)
                .path("video_material").get(0).path("video_id").asText());
        assertFalse(work.publishedEdit.members.getFirst().before.get(0)
                .path("video_material").get(0).has("aweme_item_id"));
        assertEquals("CREATED",read("plan").publishStatus);
        verify(ads,never()).automaticContent(any(),any(),any(),any());
    }
    CreationState adjust(CreationState s,String... ids){return store.mutate("tenant",s.planId,s.revision,v->{var next=Arrays.stream(ids).map(this::product).toList();CreationProductSelection.apply(v,next);v.products=new ArrayList<>(next);for(String id:ids)if(v.materials.stream().noneMatch(m->m.productId.equals(id)))v.materials.add(material(id));v.generationStatus="SUCCEEDED";});}
    @Test void beginAndResumeDoNotCreateOrModifyLivePlan(){var work=service.begin(tenant,"plan");assertNotEquals("plan",work.planId);assertEquals(work.planId,service.begin(tenant,"plan").planId);assertEquals("CREATED",read("plan").publishStatus);assertEquals("original-engine-task",work.materials.getFirst().enginePlanId);verify(plans,never()).save(any());verify(ads,never()).automaticContent(any(),any(),any(),any());verify(ads,never()).deleteProduct(any(),any(),any());}
    @Test void saveAdditionKeepsOriginalVisualAndUpdatesOriginalPhysicalPlan(){var w=adjust(service.begin(tenant,"plan"),"123","456");var result=service.save(tenant,w.planId,w.revision);assertEquals("SUCCEEDED",result.publishedEdit.status);assertEquals(List.of("123","456"),read("plan").products.stream().map(CreationState.Product::qcProductId).toList());assertEquals(2,read("plan").materials.size());assertEquals("1001",read("plan").qcPlanId);assertNull(read("plan").publishedEditDraftId);assertNull(lock);verify(ads).automaticContent(eq(credential),eq("1001"),eq("MATERIAL_ADD"),any());verify(plans,never()).save(any());}
    @Test void replacementAddsNewBeforeRemovingLastOldProduct(){var w=adjust(service.begin(tenant,"plan"),"456");assertEquals("SUCCEEDED",service.save(tenant,w.planId,w.revision).publishedEdit.status);var order=inOrder(ads);order.verify(ads).automaticContent(eq(credential),eq("1001"),eq("MATERIAL_ADD"),any());order.verify(ads).deleteProduct(credential,"1001","123");assertEquals("456",read("plan").materials.getFirst().productId);}
    @Test void deletionDoesNotGenerateOrRemoveOtherProductsMedia(){var source=store.mutate("tenant","plan",null,s->{s.products.add(product("456"));s.materials.add(material("456"));});remote.put("1001",wrap("1001",creatives(source)));var w=adjust(service.begin(tenant,"plan"),"456");assertEquals("SUCCEEDED",service.save(tenant,w.planId,w.revision).publishedEdit.status);verify(ads).deleteProduct(credential,"1001","123");verify(ads,never()).automaticContent(any(),any(),any(),any());assertEquals("video-456",read("plan").materials.getFirst().videoAssetId);}
    @Test void timeoutNeverReplaysWriteAndReadbackCompletesOriginalPlan(){var w=adjust(service.begin(tenant,"plan"),"123","456");doThrow(new BizException(1406,"timeout")).when(ads).automaticContent(any(),any(),any(),any());var pending=service.save(tenant,w.planId,w.revision);assertEquals("UNCERTAIN",pending.publishedEdit.status);assertEquals(1,read("plan").products.size());store.mutate("tenant",w.planId,null,s->s.publishedEdit.nextCheckAt=0);service.verify(tenant,w.planId);verify(ads,times(1)).automaticContent(any(),any(),any(),any());remote.put("1001",wrap("1001",pending.publishedEdit.members.getFirst().after));store.mutate("tenant",w.planId,null,s->s.publishedEdit.nextCheckAt=0);assertEquals("SUCCEEDED",service.verify(tenant,w.planId).publishedEdit.status);verify(ads,times(1)).automaticContent(any(),any(),any(),any());}
    @Test void externalChangesOrStaleRevisionAreRejectedBeforeAnyWrite(){var w=adjust(service.begin(tenant,"plan"),"123","456");assertThrows(BizException.class,()->service.save(tenant,w.planId,w.revision-1));remote.put("1001",wrap("1001",json.createArrayNode().add(creatives(w).get(0)).add(creatives(adjust(w,"789")).get(0))));assertThrows(BizException.class,()->service.save(tenant,w.planId,read(w.planId).revision));verify(ads,never()).automaticContent(any(),any(),any(),any());assertNull(lock);}
    @Test void linkedPlansBothConfirmBeforeSharedLocalStateChanges(){linked=true;var w=adjust(service.begin(tenant,"plan"),"123","456");assertEquals("SUCCEEDED",service.save(tenant,w.planId,w.revision).publishedEdit.status);for(String id:List.of("1001","1002"))verify(ads).automaticContent(eq(credential),eq(id),eq("MATERIAL_ADD"),any());for(String id:List.of("plan","cost"))verify(metadata).verifiedContent(eq(tenant),eq(id),any(),any());assertNull(lock);}
    @Test void discardDropsOnlyWorkingPreviewAndRetainsPublishedAssets(){var w=adjust(service.begin(tenant,"plan"),"456");service.discard(tenant,w.planId);assertThrows(BizException.class,()->read(w.planId));assertEquals("123",read("plan").materials.getFirst().productId);assertNull(read("plan").publishedEditDraftId);verify(ads,never()).deleteProduct(any(),any(),any());}
    @Test void replacingTenProductsNeverExceedsPlatformLimitOrDeletesLastProduct(){var source=store.mutate("tenant","plan",null,s->{s.products.clear();s.materials.clear();for(int i=1;i<=10;i++){s.products.add(product(""+i));s.materials.add(material(""+i));}});remote.put("1001",wrap("1001",creatives(source)));var w=adjust(service.begin(tenant,"plan"),"21","22","23","24","25","26","27","28","29","30");assertEquals("SUCCEEDED",service.save(tenant,w.planId,w.revision).publishedEdit.status);assertEquals(10,read("plan").products.size());}
    @Test void unknownPlatformVisualsArePreservedByDelta(){var source=read("plan");var before=creatives(source);((ObjectNode)before.get(0)).withArray("video_material").addObject().put("video_id","external");var changed=json.convertValue(source,CreationState.class);changed.products.add(product("456"));changed.materials.add(material("456"));var delta=PublishedCompositionService.merge(before,creatives(source),creatives(changed));assertTrue(PublishedMaterialService.objects(delta.finalGroups().get(0)).contains("VIDEO:external"));assertEquals(before,delta.additions());assertEquals(1,source.products.size());}
    @Test void activeTasksPreventDiscardAndSelectionSideEffects(){var w=service.begin(tenant,"plan");store.mutate("tenant",w.planId,null,s->s.generationStatus="RUNNING");assertThrows(BizException.class,()->service.discard(tenant,w.planId));verify(ads,never()).deleteProduct(any(),any(),any());}
    @Test void anIndividualPendingGenerationLocksEntryAfterTheBatchHasEnded(){
        store.mutate("tenant","plan",null,s->{s.generationStatus="FAILED";var addition=new CreationState.GenerationAddition();addition.status="RUNNING";s.generationAdditions.add(addition);});
        assertThrows(BizException.class,()->service.begin(tenant,"plan"));assertNull(read("plan").publishedEditDraftId);verifyNoInteractions(ads);
    }
    @Test void publicDtoDoesNotExposeBaselineOrPlatformReceipts()throws Exception{var w=service.begin(tenant,"plan");var view=json.valueToTree(com.lynx.ad.modules.delivery.api.dto.CreationView.of(w));assertFalse(view.toString().contains("original-engine-task"));assertFalse(view.has("publishedEdit"));assertEquals("plan",view.path("contentEdit").path("targetPlanId").asText());}
    @Test void retainedProductRemovesOnlyVerifiedNativeMaterialBinding(){
        var source=store.mutate("tenant","plan",null,s->{var extra=material("123");extra.id="second";extra.videoAssetId="second-video";s.materials.add(extra);});remote.put("1001",wrap("1001",creatives(source)));
        var work=service.begin(tenant,"plan");work=store.mutate("tenant",work.planId,null,s->{s.materials.removeFirst();s.mediaChanged();});
        var report=json.createObjectNode();report.putArray("product_info").addObject().put("product_id","123");report.putObject("material_info").putObject("video_material").put("video_id","video-123").put("material_id","501");
        when(ads.materialReport(eq(credential),eq("1001"),any(),any(),eq("VIDEO"))).thenReturn(List.of(report));
        doAnswer(i->{remote.put("1001",wrap("1001",PublishedMaterialService.without(remote.get("1001").path("multi_product_creative_list"),"123",Set.of("VIDEO:video-123"))));return null;}).when(ads).deleteMaterials(credential,"1001",List.of("501"));
        assertEquals("SUCCEEDED",service.save(tenant,work.planId,work.revision).publishedEdit.status);assertEquals(List.of("second"),read("plan").materials.stream().map(m->m.id).toList());verify(ads).deleteMaterials(credential,"1001",List.of("501"));verify(ads,never()).deleteProduct(any(),any(),any());
    }
    @Test void wrongTenantCannotResumeOrSaveOriginalPlan(){var work=service.begin(tenant,"plan");assertThrows(BizException.class,()->service.save(new TenantId("other"),work.planId,work.revision));verify(ads,never()).automaticContent(any(),any(),any(),any());}
    @Test void editedAndDeletedTitlesReplaceOnlyKnownCopiesAndKeepExternalTitles(){
        var old=creatives(read("plan"));var before=old.deepCopy();((ObjectNode)before.get(0)).withArray("title_material").addObject().put("title","平台另外添加的标题").put("title_type","CUSTOM");
        var wanted=old.deepCopy();var titles=((ObjectNode)wanted.get(0)).putArray("title_material");titles.addObject().put("title","重新生成后的商品标题").put("title_type","CUSTOM");
        var finalGroup=PublishedCompositionService.merge(before,old,wanted).finalGroups().get(0);
        assertEquals(List.of("重新生成后的商品标题","平台另外添加的标题"),java.util.stream.StreamSupport.stream(finalGroup.path("title_material").spliterator(),false).map(t->t.path("title").asText()).toList());
        assertEquals(before.get(0).path("video_material"),finalGroup.path("video_material"));
    }
}
