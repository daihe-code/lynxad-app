package com.lynx.ad.modules.delivery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.application.CreationAssetImporter;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationProductTitlePoolTest {
    CreationStore store=mock(CreationStore.class);CreationAssetImporter importer=mock(CreationAssetImporter.class);
    CreationState s=new CreationState();CreationWorkflowService service;
    @BeforeEach void setup(){
        s.tenantId="tenant";s.userId="user";s.planId="plan";s.revision=7;s.generationStatus="SUCCEEDED";
        s.products.add(new CreationState.Product("123","123","商品",null,null,null,"",null,List.of(),List.of()));
        for(int i=0;i<2;i++){var m=new CreationState.Material();m.id="m"+i;m.productId="123";m.type="VIDEO";m.status="READY";m.videoAssetId="v"+i;m.coverAssetId="cover"+i;m.title="素材名称"+i;m.titles=new ArrayList<>(List.of(i==0?"原商品视频标题":"其他商品视频标题"));s.materials.add(m);}
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(importer.ingest(any(),any(),any(),any(),eq("TEXT"),isNull(),eq(false))).thenReturn("new-text");
        service=new CreationWorkflowService(store,null,null,null,null,new ObjectMapper(),importer,null,null,null,mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
    }
    @Test void copyEditKeepsVisualsAndNamesAndInvalidatesOnlyPublishVersion(){
        var names=s.materials.stream().map(m->m.title).toList();int version=s.packageVersion;
        service.productTitles(new TenantId("tenant"),"plan","123",new CreationWorkflowService.ProductTitles(7,"CUSTOM",List.of("更新后的商品文案标题","其他商品视频标题")));
        assertEquals(names,s.materials.stream().map(m->m.title).toList());assertTrue(s.materials.stream().allMatch(m->"READY".equals(m.status)));
        assertEquals("v0",s.materials.getFirst().videoAssetId);assertEquals("cover0",s.materials.getFirst().coverAssetId);
        assertEquals("new-text",s.titlePools.get("123").customTextAssetId);assertEquals(version+1,s.packageVersion);assertNull(s.confirmedVersion);
        verify(importer,times(1)).ingest(any(),any(),any(),any(),eq("TEXT"),isNull(),eq(false));
    }
    @Test void unchangedPoolSkipsImportAndForeignProductCannotEdit(){
        service.productTitles(new TenantId("tenant"),"plan","123",new CreationWorkflowService.ProductTitles(7,"CUSTOM",List.of("原商品视频标题","其他商品视频标题")));
        verifyNoInteractions(importer);assertTrue(s.titlePools.isEmpty());
        assertThrows(BizException.class,()->service.productTitles(new TenantId("tenant"),"plan","foreign",new CreationWorkflowService.ProductTitles(7,"CUSTOM",List.of("原商品视频标题"))));
        verifyNoInteractions(importer);
    }
    @Test void emptyDuplicateAndStaleCopiesNeverReachImporter(){
        for(var titles:List.of(List.<String>of(),List.of("重复商品标题","重复商品标题")))assertThrows(BizException.class,()->service.productTitles(new TenantId("tenant"),"plan","123",new CreationWorkflowService.ProductTitles(7,"CUSTOM",titles)));
        assertThrows(BizException.class,()->service.productTitles(new TenantId("tenant"),"plan","123",new CreationWorkflowService.ProductTitles(6,"CUSTOM",List.of("原商品视频标题"))));verifyNoInteractions(importer);
    }
    @Test void deletingAndManuallyEditingTitlesPersistAuthoritativePoolAndProvenance(){
        s.materials.forEach(m->m.engineMaterialId="engine-"+m.id);
        service.productTitles(new TenantId("tenant"),"plan","123",new CreationWorkflowService.ProductTitles(7,"CUSTOM",List.of("原商品视频标题")));
        assertEquals(List.of("原商品视频标题"),com.lynx.ad.modules.delivery.api.dto.CreationView.of(s).titlePools().get("123").customTitles());
        assertEquals(List.of("原商品视频标题"),s.titlePools.get("123").customGeneratedTitles);
        service.productTitles(new TenantId("tenant"),"plan","123",new CreationWorkflowService.ProductTitles(s.revision,"CUSTOM",List.of("自己修改后的商品文案")));
        assertTrue(s.titlePools.get("123").customGeneratedTitles.isEmpty());
        assertEquals(2,s.materials.size());
    }
    @Test void regeneratingTitlesChecksRevisionAndPublishStateBeforeCallingEngine(){
        var generator=mock(com.lynx.ad.modules.delivery.application.CreationTitleRegeneration.class);service.titleRegeneration(generator);
        assertThrows(BizException.class,()->service.regenerateTitle(new TenantId("tenant"),"plan","123",new CreationWorkflowService.RegenerateTitle(6,"CUSTOM",0,"原商品视频标题")));
        s.publishStatus="CREATED";
        assertThrows(BizException.class,()->service.regenerateTitle(new TenantId("tenant"),"plan","123",new CreationWorkflowService.RegenerateTitle(7,"CUSTOM",0,"原商品视频标题")));
        verifyNoInteractions(generator);
        s.publishStatus="NONE";s.publishedEdit=new CreationState.PublishedEdit();
        service.regenerateTitle(new TenantId("tenant"),"plan","123",new CreationWorkflowService.RegenerateTitle(7,"CUSTOM",0,"原商品视频标题"));
        verify(generator).submit(s,"123","CUSTOM",0,"原商品视频标题");
    }
}
