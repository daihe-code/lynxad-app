package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;

class CreationSharedMaterialsTest {
    final TenantId tenant=new TenantId("tenant");final ObjectMapper json=new ObjectMapper();
    final CreationStore store=mock(CreationStore.class);final PlanTransferStore transfers=mock(PlanTransferStore.class);
    final AdPlanRepository plans=mock(AdPlanRepository.class);final AssetUploadService assets=mock(AssetUploadService.class);
    final CreationAssetImporter importer=mock(CreationAssetImporter.class);
    CreationWorkflowService service;CreationState state;AdPlan root,cost;
    @BeforeEach void setup() {
        root=plan("volume",SmartBidType.CONSERVATIVE,"account");cost=plan("cost",SmartBidType.CUSTOM,"account");
        var group=new PlanTransferStore.Group("volume","volume","volume","cost",null);
        when(transfers.group(eq(tenant),any())).thenReturn(group);
        when(plans.findById(tenant,root.id())).thenReturn(Optional.of(root));when(plans.findById(tenant,cost.id())).thenReturn(Optional.of(cost));
        state=new CreationState();state.planId="volume";state.tenantId="tenant";state.userId="user";state.accountId="account";state.awemeId="999";
        state.publishStatus="CREATED";
        var video=new CreationState.Material();video.id="video";video.type="VIDEO";video.videoAssetId="video-file";video.coverAssetId="cover-file";video.status="READY";
        var carousel=new CreationState.Material();carousel.id="carousel";carousel.type="CAROUSEL";carousel.imageAssetIds.addAll(List.of("image1","image2"));carousel.audioAssetId="audio";carousel.status="READY";
        var title=new CreationState.Material();title.id="title";title.type="TITLE";title.title="共享标题";title.titles.add(title.title);title.status="READY";
        state.materials.addAll(List.of(video,carousel,title));
        when(store.get("tenant","volume")).thenAnswer(i->json.convertValue(state,CreationState.class));
        when(store.mutate(eq("tenant"),eq("volume"),any(),any())).thenAnswer(i->{
            Integer revision=i.getArgument(2);if(revision!=null&&revision!=state.revision)throw new BizException(1404,"草稿已更新");
            Consumer<CreationState> update=i.getArgument(3);update.accept(state);return json.convertValue(state,CreationState.class);
        });
        when(assets.detail(eq(tenant),anyString())).thenAnswer(i->CreationPayloadTest.asset(i.getArgument(1),"IMAGE","APPROVED",null,null,null));
        service=new CreationWorkflowService(store,plans,null,null,null,json,importer,assets,null,null,mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
        service.transferStore(transfers);
    }
    private AdPlan plan(String id,SmartBidType mode,String account) {
        return new AdPlan(new PlanId(id),tenant,new AccountId(account),new AwemeId("999"),new PlanName(id),PlanStatus.PAUSED,PlanSource.LINGTOU,
                new DeliverySetting(mode,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(),new QcPlanId(id+"qc"),"DISABLE",null,1,null,List.of());
    }
    @Test void transferredPlanReadsOriginalVideoCarouselCoverAndTitlesWithoutItsOwnDraft() {
        var original=service.get(tenant,"volume");var transferred=service.get(tenant,"cost");
        assertEquals("volume",transferred.planId);assertEquals(3,transferred.materials.size());
        assertEquals(original.materials.get(0).previewUrl,transferred.materials.get(0).previewUrl);
        assertEquals(original.materials.get(0).posterUrl,transferred.materials.get(0).posterUrl);
        assertEquals(List.of("url","url"),transferred.materials.get(1).imageUrls);assertEquals("url",transferred.materials.get(1).previewUrl);
        assertEquals("共享标题",transferred.materials.get(2).title);verify(store,never()).get("tenant","cost");verifyNoInteractions(importer);
    }
    @Test void deletingOriginalPhysicalPlanDoesNotDeleteSurvivingModesSharedMaterialRoot() {
        root.markDeleted();
        when(transfers.group(tenant,cost.id())).thenReturn(new PlanTransferStore.Group("volume","cost",null,"cost",null));
        var remaining=service.get(tenant,"cost");
        assertEquals("volume",remaining.planId);assertEquals(3,remaining.materials.size());
        assertEquals("video-file",remaining.materials.get(0).videoAssetId);assertEquals("共享标题",remaining.materials.get(2).title);
    }
    @Test void sharedTitleUpdateFromEitherMemberIsVisibleFromBothAndUsesOneRevision() {
        state.publishStatus="NONE";when(importer.ingest(any(),any(),anyString(),anyString(),eq("TEXT"),isNull(),eq(false))).thenReturn("new-title");
        int revision=state.revision;
        service.titles(tenant,"cost","title",new CreationWorkflowService.Titles(revision,List.of("新共享标题")));
        assertEquals("新共享标题",service.get(tenant,"volume").materials.get(2).title);
        assertEquals("新共享标题",service.get(tenant,"cost").materials.get(2).title);
        assertThrows(BizException.class,()->service.recheck(tenant,"volume","title",new CreationWorkflowService.Version(revision)));
        verify(store,never()).mutate(eq("tenant"),eq("cost"),any(),any());
    }
    @Test void publishedStateCannotBeChangedByUsingTheOtherMembersId() {
        var error=assertThrows(BizException.class,()->service.titles(tenant,"cost","title",new CreationWorkflowService.Titles(state.revision,List.of("不应写入"))));
        assertEquals(1403,error.code());assertEquals("共享标题",state.materials.get(2).title);verifyNoInteractions(importer);
    }
    @Test void accountMismatchPreventsSharedRead() {
        when(plans.findById(tenant,cost.id())).thenReturn(Optional.of(plan("cost",SmartBidType.CUSTOM,"other-account")));
        assertThrows(BizException.class,()->service.get(tenant,"cost"));verifyNoInteractions(store,assets);
    }
    @Test void anotherTenantCannotResolveTheSharedRoot() {
        when(store.get("other","cost")).thenThrow(new BizException(1403,"不属于当前账户"));
        assertThrows(BizException.class,()->service.get(new TenantId("other"),"cost"));
        verify(store,never()).get("other","volume");verifyNoInteractions(assets);
    }
    @Test void pendingSwitchBlocksSharedChangesBeforeAnyAssetWrite() {
        doThrow(new BizException(1523,"转投待核查")).when(transfers).assertIdle(tenant,cost.id());
        assertThrows(BizException.class,()->service.titles(tenant,"cost","title",new CreationWorkflowService.Titles(state.revision,List.of("标题"))));
        verifyNoInteractions(store,importer);
    }
}
