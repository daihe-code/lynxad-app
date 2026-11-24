package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.api.dto.PlanTransferRequest;
import com.lynx.ad.modules.delivery.application.PlanTransferService;
import com.lynx.ad.modules.delivery.application.PlanManagementService;
import com.lynx.ad.modules.delivery.application.PlanQueryService;
import com.lynx.ad.modules.delivery.api.dto.PlanSettingsRequest;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.common.security.TenantPrincipal;
import com.lynx.ad.common.security.SysRole;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

@EnabledIfEnvironmentVariable(named="LYNXAD_TRANSFER_TEST_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:55406/lynxad_transfer_verify_rebuild.*")
class PlanTransferRebuildIT {
    private static DriverManagerDataSource ds;
    private static JdbcTemplate jdbc;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final TenantId tenant=new TenantId("rebuild-tenant");
    private final AccountId account=new AccountId("rebuild-account");
    private final PlanId root=new PlanId("cost-plan");
    private final AwemeId aweme=new AwemeId("999");
    private final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    private final Credential credential=new Credential("fixture-token",new AdvertiserId("123"),account.value());
    private final AdPlanRepository plans=mock(AdPlanRepository.class);
    private final CreationStore creations=mock(CreationStore.class);
    private final CreationQianchuanGateway platform=mock(CreationQianchuanGateway.class);
    private final AdListPort listing=mock(AdListPort.class);
    private final AdManagementPort management=mock(AdManagementPort.class);
    private final Map<String,AdPlan> local=new HashMap<>();
    private final Map<String,ObjectNode> upstream=new HashMap<>();
    private final List<String> writes=new ArrayList<>();
    private PlanRemoteStore remote;
    private PlanTransferStore transfers;
    private PlanTransferRebuildStore rebuilds;
    private PlanTransferService service;
    private int nextId=201;
    private int createRejections;
    private boolean loseCreateReceipt;
    private boolean loseDeleteReceipt;
    private boolean omitCardsInDetail;
    private boolean loseSettingsReceipt;

    @BeforeAll static void schema() throws Exception {
        ds=new DriverManagerDataSource(System.getenv("LYNXAD_TRANSFER_TEST_URL"),"root",System.getenv("LYNXAD_TRANSFER_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE IF NOT EXISTS ad_plan(id VARCHAR(64) NOT NULL,tenant_id VARCHAR(64) NOT NULL,PRIMARY KEY(tenant_id,id))");
        for(var migration:List.of(
                Map.entry("qc_plan_metadata","V9__qianchuan_management.sql"),
                Map.entry("delivery_transfer_group","V22__plan_transfer.sql"),
                Map.entry("delivery_plan_settings_edit","V36__plan_settings_edit.sql"),
                Map.entry("delivery_transfer_history","V63__rebuild_plan_transfer.sql"))) {
            Integer found=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,migration.getKey());
            if(found==0)try(var c=ds.getConnection()){
                ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/"+migration.getValue()));
                if(migration.getKey().equals("qc_plan_metadata"))
                    ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V23__preserve_plan_statistics.sql"));
            }
        }
    }

    @BeforeEach void fixture() throws Exception {
        for(var table:List.of("delivery_transfer_history","delivery_transfer_rebuild","delivery_transfer_attempt","delivery_transfer_group","delivery_plan_settings_edit","qc_plan_metadata"))
            jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",tenant.value());
        var manager=new DataSourceTransactionManager(ds);
        transfers=new PlanTransferStore(jdbc,manager);
        rebuilds=new PlanTransferRebuildStore(jdbc,json);
        remote=new PlanRemoteStore(jdbc,json);
        var gateway=new PlanTransferGateway(platform,listing,new LynxAdProperties(),json,management);
        service=new PlanTransferService(plans,transfers,creations,remote,gateway,mock(BillingTrialService.class),json,manager);
        service.rebuildStore(rebuilds);
        when(management.insightReport(eq(credential),anyString(),any(),any(),eq(true)))
                .thenReturn(new AdManagementPort.InsightReport(json.createObjectNode(),List.of(),"stat_time_day",Map.of(),null));
        when(management.detail(eq(credential),anyString())).thenAnswer(i->upstream.get(i.<String>getArgument(1)).deepCopy());
        doAnswer(i->{
            var detail=upstream.get(i.<String>getArgument(1));
            String name=i.getArgument(2);BigDecimal budget=i.getArgument(3);
            if(name!=null)detail.put("name",name);
            if(budget!=null)detail.withObject("/delivery_setting").put("budget",budget);
            if(loseSettingsReceipt){loseSettingsReceipt=false;throw new BizException(1406,"修改响应超时");}
            return null;
        }).when(management).settings(eq(credential),anyString(),nullable(String.class),nullable(BigDecimal.class),isNull());
        doAnswer(i->{
            var ds=upstream.get(i.<String>getArgument(1)).withObject("/delivery_setting");
            String type=i.getArgument(2);LocalDate end=i.getArgument(3);
            ds.put("video_schedule_type","DATE_RANGE".equals(type)?"SCHEDULE_START_END":"SCHEDULE_FROM_NOW");
            if(end!=null)ds.put("start_time",today.toString()).put("end_time",end.toString());
            return null;
        }).when(management).schedule(eq(credential),anyString(),anyString(),nullable(LocalDate.class));

        var published=(ObjectNode)json.readTree("""
                {"advertiser_id":123,"aweme_id":999,"name":"控成本测试计划","marketing_goal":"VIDEO_PROM_GOODS","product_ids":[101],
                 "delivery_setting":{"smart_bid_type":"SMART_BID_CUSTOM","budget":500,"roi2_goal":4.96,"video_schedule_type":"SCHEDULE_FROM_NOW",
                   "deep_external_action":"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI"},
                 "multi_product_creative_list":[{"product_id":101,"hide_in_aweme":true,
                   "video_material":[{"image_mode":"VIDEO_VERTICAL","video_id":"video1","video_cover_id":"cover1"}],
                   "title_material":[{"title":"完整素材标题","title_type":"CUSTOM"}],
                   "creative_card":{"promotion_card_image_id":"cover1","promotion_card_title":"视频同款商品"}}]}
                """);
        var source=new AdPlan(root,tenant,account,aweme,new PlanName("控成本测试计划"),PlanStatus.DELIVERING,PlanSource.LINGTOU,
                new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(500),Optional.of(new RoiGoal(new BigDecimal("4.96"))),Schedule.fromNow(),AudienceJson.empty()),
                List.of(new QcProductId("101")),new QcPlanId("101"),"DELIVERY_OK",null,1,null,List.of());
        local.put(root.value(),source);
        upstream.put("101",published.deepCopy().put("ad_id","101").put("status","DELIVERY_OK"));
        when(creations.publishedInstancePayload(tenant.value(),root.value(),"101")).thenReturn(Optional.of(published));
        when(plans.findById(eq(tenant),any())).thenAnswer(i->Optional.ofNullable(local.get(((PlanId)i.getArgument(1)).value())));
        when(plans.findByIdForUpdate(eq(tenant),any())).thenAnswer(i->plans.findById(tenant,i.getArgument(1)));
        doAnswer(i->{AdPlan p=i.getArgument(0);local.put(p.id().value(),p);return null;}).when(plans).save(any());
        doAnswer(i->{plans.save(i.getArgument(0));return null;}).when(plans).saveTransferInstance(any(),nullable(String.class));
        when(platform.credential(tenant,account.value(),aweme.value())).thenReturn(credential);
        when(platform.detail(eq(tenant),eq(account.value()),eq(aweme.value()),anyString()))
                .thenAnswer(i->{
                    var result=upstream.get(i.<String>getArgument(3)).deepCopy();
                    if(omitCardsInDetail)for(var group:result.path("multi_product_creative_list"))
                        ((ObjectNode)group).putNull("creative_card").put("creative_type","PROGRAMMATIC_CREATIVE");
                    result.putObject("programmatic_creative_media_list").putArray("video_material");
                    return result;
                });
        when(platform.recommendations(eq(tenant),eq(account.value()),eq(aweme.value()),anyList(),any(),anyString())).thenReturn(json.createObjectNode());
        when(platform.write(eq(credential),any(),any())).thenAnswer(i->{
            JsonNode body=i.getArgument(2);
            var id=body.path("ad_ids").get(0).asText();
            assertEquals("DELETE",body.path("opt_status").asText());
            writes.add("DELETE:"+id);
            upstream.get(id).put("status","DELETE");
            if(loseDeleteReceipt){loseDeleteReceipt=false;throw new BizException(1523,"删除响应超时");}
            var reply=json.createObjectNode();
            reply.putArray("results").addObject().put("ad_id",id).put("flag",true);
            return reply;
        });
        when(platform.create(eq(tenant),eq(account.value()),eq(aweme.value()),any())).thenAnswer(i->{
            assertEquals(0,deliveryCount(),"商品互斥仍存在");
            ObjectNode body=i.getArgument(3);
            var mode=body.path("delivery_setting").path("smart_bid_type").asText();
            writes.add("CREATE:"+mode);
            if(createRejections>0){createRejections--;throw new BizException(1406,"平台拒绝测试计划");}
            String id=Integer.toString(nextId++);
            upstream.put(id,body.deepCopy().put("ad_id",id).put("status","DELIVERY_OK"));
            if(loseCreateReceipt){loseCreateReceipt=false;throw new BizException(1523,"创建响应超时");}
            return id;
        });
        remote.save(tenant,account,"101",root.value(),published);
        remote.detail(tenant,root.value(),upstream.get("101"));
        saveReport(root,"128.50","510.00","3.97",8);
    }

    @Test void costToVolumeAndRestoreCostKeepTwoLogicalPlansAndHistoricalReports() {
        assertTrue(service.context(tenant,root).available());
        var first=service.transfer(tenant,root,volume("round-trip-1"));
        assertEquals("SUCCEEDED",first.status(),first.message());
        var volume=new PlanId(first.planId());
        assertEquals("201",first.qcPlanId());
        assertArchived(root,"101","128.50");
        assertEquals("50",local.get(volume.value()).setting().dailyBudget().amount().stripTrailingZeros().toPlainString());
        saveReport(volume,"23.00","92","4",2);

        var restored=service.enableExisting(tenant,root);
        assertEquals("SUCCEEDED",restored.status(),restored.message());
        assertEquals(root.value(),restored.planId());
        assertEquals("202",restored.qcPlanId());
        assertArchived(volume,"201","23.00");
        var active=local.get(root.value());
        assertEquals("控成本测试计划",active.name().text());
        assertEquals(PlanStatus.DELIVERING,active.status());
        assertEquals(new BigDecimal("4.96"),active.setting().roi2Goal().orElseThrow().value());
        assertEquals(0,new BigDecimal("500").compareTo(active.setting().dailyBudget().amount()));
        assertNull(remote.list(tenant).get(root.value()).lastReport(),"新实例应有自己的数据");
        assertEquals(2,local.size());
        assertEquals(1,deliveryCount());
        assertEquals(List.of("DELETE:101","CREATE:SMART_BID_CONSERVATIVE","DELETE:201","CREATE:SMART_BID_CUSTOM"),writes);
        var historical=service.history(tenant,root).getFirst();
        assertEquals("101",historical.qcPlanId());
        assertEquals(0,new BigDecimal("128.50").compareTo(historical.costYuan()));
        assertEquals(0,new BigDecimal("510").compareTo(historical.gmvYuan()));
        assertEquals(today,historical.statisticsDate());
        System.out.printf("ROUND TRIP cost 101 → volume 201 → cost 202; archives %s, %s; one active%n",historical.qcPlanId(),service.history(tenant,volume).getFirst().qcPlanId());
    }

    @Test void unpairedPausedPlanCanContinueToFirstTransferAfterExplicitResume() {
        var source=local.get(root.value());
        source.apply(PlanStatus.PAUSED);
        upstream.get("101").put("opt_status","DISABLE");

        var context=service.context(tenant,root);
        assertTrue(context.requiresResume());
        assertFalse(context.reuse());
        assertFalse(context.available());
        assertThrows(BizException.class,()->service.transfer(tenant,root,volume("while-paused")));
        assertTrue(writes.isEmpty());

        source.apply(PlanStatus.DELIVERING);
        upstream.get("101").remove("opt_status");
        context=service.context(tenant,root);
        assertFalse(context.requiresResume());
        assertTrue(context.available());
        assertEquals("SUCCEEDED",service.transfer(tenant,root,volume("after-resume")).status());
        assertTrue(source.transferArchived());
        assertArchivedHistoryRetained(root,"101");
        assertEquals(1,deliveryCount());
    }

    @Test void pausedPlanWithMissingSnapshotCannotResumeFromTransfer() {
        local.get(root.value()).apply(PlanStatus.PAUSED);
        upstream.get("101").put("opt_status","DISABLE");
        when(creations.publishedInstancePayload(tenant.value(),root.value(),"101")).thenReturn(Optional.empty());
        var context=service.context(tenant,root);
        assertFalse(context.requiresResume());
        assertFalse(context.available());
        assertTrue(writes.isEmpty());
    }

    @Test void restoringVolumeRecoversTheEstimateForTheSameBudgetFromAnOlderArchive() throws Exception {
        var recommendation=json.readTree("""
                {"budget":{"budget":50,"estimate_trans_cost_list":[{"budget":50,"min_estimate_convert":3,"estimate_convert":5,
                    "min_estimate_roi":0.15,"estimate_roi":0.19}]}}
                """);
        when(platform.recommendations(eq(tenant),eq(account.value()),eq(aweme.value()),anyList(),eq(SmartBidType.CONSERVATIVE),anyString()))
            .thenReturn(recommendation,json.createObjectNode());
        var first=service.transfer(tenant,root,volume("volume-estimate-first"));
        assertEquals("SUCCEEDED",first.status(),first.message());
        var volumeId=new PlanId(first.planId());
        assertEquals("SUCCEEDED",service.enableExisting(tenant,root).status());
        jdbc.update("""
                UPDATE delivery_transfer_history SET snapshot=JSON_REMOVE(snapshot,
                  '$.createPayload.delivery_setting.min_estimate_convert', '$.createPayload.delivery_setting.estimate_convert',
                  '$.createPayload.delivery_setting.min_estimate_roi_goal', '$.createPayload.delivery_setting.estimate_roi_goal')
                WHERE tenant_id=? AND plan_id=?
                """,tenant.value(),volumeId.value());
        var restored=service.enableExisting(tenant,volumeId);
        assertEquals("SUCCEEDED",restored.status(),restored.message());
        var settings=upstream.get(restored.qcPlanId()).path("delivery_setting");
        assertEquals(3,settings.path("min_estimate_convert").asInt());
        assertEquals(5,settings.path("estimate_convert").asInt());
        assertEquals(0.15,settings.path("min_estimate_roi_goal").asDouble());
        assertEquals(0.19,settings.path("estimate_roi_goal").asDouble());
        assertEquals(1,deliveryCount());
    }

    @Test void changedRecommendedBudgetRequiresItsOwnEstimateBeforeDeletingTheActivePlan() throws Exception {
        var atFifty=json.readTree("""
                {"budget":{"budget":50,"estimate_trans_cost_list":[{"budget":50,"min_estimate_convert":3,"estimate_convert":5,
                    "min_estimate_roi":0.15,"estimate_roi":0.19}]}}
                """);
        var emptyAtHundred=json.readTree("{\"budget\":{\"budget\":100,\"estimate_trans_cost_list\":[]}}");
        var atHundred=json.readTree("""
                {"budget":{"budget":100,"estimate_trans_cost_list":[{"budget":100,"min_estimate_convert":6,"estimate_convert":8,
                    "min_estimate_roi":0.17,"estimate_roi":0.25}]}}
                """);
        when(platform.recommendations(eq(tenant),eq(account.value()),eq(aweme.value()),anyList(),eq(SmartBidType.CONSERVATIVE),anyString()))
            .thenReturn(atFifty,emptyAtHundred,atHundred);
        var first=service.transfer(tenant,root,volume("volume-estimate-budget"));
        assertEquals("SUCCEEDED",first.status(),first.message());
        var volumeId=new PlanId(first.planId());
        assertEquals("SUCCEEDED",service.enableExisting(tenant,root).status());
        var plan=local.get(volumeId.value());
        var old=plan.setting();
        plan.updateSetting(new DeliverySetting(old.bidType(),Money.ofYuan(100),old.roi2Goal(),old.schedule(),old.audience()));
        int before=writes.size();
        assertEquals(1403,assertThrows(BizException.class,()->service.enableExisting(tenant,volumeId)).code());
        assertEquals(before,writes.size());
        var restored=service.enableExisting(tenant,volumeId);
        assertEquals("SUCCEEDED",restored.status(),restored.message());
        var settings=upstream.get(restored.qcPlanId()).path("delivery_setting");
        assertEquals(100,settings.path("budget").asInt());
        assertEquals(8,settings.path("estimate_convert").asInt());
        assertEquals(0.25,settings.path("estimate_roi_goal").asDouble());
    }

    @Test void fullDefaultNameSurvivesRebuildAndPlatformNamesOnlyMatchTheirOwnReceipts() {
        String originalName = "2026-11-22_商品全域投放_10:37:01_e8b";
        String expectedName = originalName + "_转投";
        var source = local.get(root.value());
        source.rename(PlanName.fromPlatform(originalName));
        upstream.get("101").put("name", originalName);
        assertEquals(expectedName, service.context(tenant,root).targetName());

        var transfer = service.transfer(tenant,root,volume("full-name"));
        assertEquals("SUCCEEDED",transfer.status(),transfer.message());
        var target = local.get(transfer.planId());
        assertEquals(expectedName, target.name().text());
        String instanceName = upstream.get(transfer.qcPlanId()).path("name").asText();
        assertTrue(instanceName.length() <= 30);
        assertNotEquals(expectedName, instanceName);

        target.rename(PlanName.fromPlatform(instanceName));
        assertEquals(expectedName, rebuilds.displayName(target));
        assertEquals(instanceName, rebuilds.logicalName(tenant,root.value(),transfer.qcPlanId(),instanceName));

        String legacyName = originalName.substring(0,25) + "_转投放量";
        jdbc.update("UPDATE delivery_transfer_rebuild SET target_name=? WHERE tenant_id=? AND request_key=?",
                legacyName,tenant.value(),"full-name");
        assertEquals(expectedName, rebuilds.displayName(target));
        var restored = service.enableExisting(tenant,root);
        assertEquals("SUCCEEDED",restored.status(),restored.message());
        assertEquals(originalName, local.get(root.value()).name().text());
        assertEquals(expectedName, local.get(target.id().value()).name().text());
        assertEquals(expectedName, service.context(tenant,root).targetName());
    }

    @Test void explicitTargetRejectionRebuildsOriginalWithItsBudgetAndMaterials() {
        createRejections=1;
        var first=service.transfer(tenant,root,volume("reject-and-restore"));
        assertEquals("FAILED",first.status(),first.message());
        assertTrue(first.message().contains("原投放已恢复"));
        assertEquals("201",first.qcPlanId());
        assertEquals("201",local.get(root.value()).qcPlanId().orElseThrow().value());
        assertEquals(PlanStatus.DELIVERING,local.get(root.value()).status());
        assertEquals("video1",upstream.get("201").at("/multi_product_creative_list/0/video_material/0/video_id").asText());
        assertEquals(1,deliveryCount());
        assertNull(transfers.group(tenant,root).requestKey());
        assertEquals(List.of("DELETE:101","CREATE:SMART_BID_CONSERVATIVE","CREATE:SMART_BID_CUSTOM"),writes);
        System.out.println("CREATE REJECTED: original cost delivery recovered as 201, no volume instance, archived data retained");
    }

    @Test void unknownCreateDoesNotWriteAgainAndRecoversOnlyMatchingReceipt() {
        loseCreateReceipt=true;
        var req=volume("unknown-create");
        assertEquals("UNCERTAIN",service.transfer(tenant,root,req).status());
        assertEquals("UNCERTAIN",service.transfer(tenant,root,req).status());
        assertEquals("UNCERTAIN",service.reconcile(tenant,root).status());
        assertEquals(2,writes.size());
        var created=upstream.get("201");
        when(listing.list(credential,SmartBidType.CONSERVATIVE)).thenReturn(List.of(new OceanPlanRow(
                "201",created.path("name").asText(),"DELIVERY_OK",SmartBidType.CONSERVATIVE,Money.ofYuan(50),null,null,created)));
        assertEquals("SUCCEEDED",service.reconcile(tenant,root).status());
        assertEquals(2,writes.size());
    }

    @Test void unknownDeleteRequiresConfirmationBeforeCreating() {
        loseDeleteReceipt=true;
        assertEquals("UNCERTAIN",service.transfer(tenant,root,volume("unknown-delete")).status());
        assertEquals(List.of("DELETE:101"),writes);
        assertEquals("SUCCEEDED",service.reconcile(tenant,root).status());
        assertEquals(List.of("DELETE:101","CREATE:SMART_BID_CONSERVATIVE"),writes);
    }

    @Test void incompleteCreativeSnapshotDisablesTransferBeforeAnyWrite() {
        var group=(ObjectNode)upstream.get("101").at("/multi_product_creative_list/0");
        group.remove("creative_card");
        ((ObjectNode)group.path("title_material").get(0)).put("title","已经改变的素材标题");
        assertFalse(service.context(tenant,root).available());
        assertThrows(BizException.class,()->service.transfer(tenant,root,volume("missing-card")));
        assertTrue(writes.isEmpty());
        assertEquals(1,deliveryCount());
    }

    @Test void exactInstanceReceiptFillsOmittedCardOnBothTransfersAndReadback() {
        omitCardsInDetail=true;
        assertTrue(service.context(tenant,root).available());
        var volume=service.transfer(tenant,root,volume("missing-card-matched"));
        assertEquals("SUCCEEDED",volume.status(),volume.message());
        var restore=service.enableExisting(tenant,root);
        assertEquals("SUCCEEDED",restore.status(),restore.message());
        for(String id:List.of("201","202"))
            assertEquals("cover1",upstream.get(id).at("/multi_product_creative_list/0/creative_card/promotion_card_image_id").asText());
        assertEquals(1,deliveryCount());
    }

    @Test void archivesThePlatformRangeWhenAvailableAndKeepsTheCachedDaySeparate() {
        var start=today.minusDays(12);
        upstream.get("101").put("create_time",start+" 09:00:00");
        var report=json.createObjectNode();
        report.putObject("dimensions").put("ad_id","101").put("stat_time_day",today.toString());
        report.putObject("metrics").put("stat_cost",358.5).put("total_pay_order_gmv_include_coupon_for_roi2",1650);
        when(management.insightReport(credential,"101",start,today,true))
                .thenReturn(new AdManagementPort.InsightReport(report,List.of(report),"stat_time_day",Map.of(),null));
        var result=service.transfer(tenant,root,volume("range-history"));
        assertEquals("SUCCEEDED",result.status(),result.message());
        var archived=service.history(tenant,root).getFirst();
        assertEquals(start,archived.statisticsStartDate());
        assertEquals(today,archived.statisticsDate());
        assertEquals(0,new BigDecimal("358.50").compareTo(archived.costYuan()));
        assertEquals(0,new BigDecimal("128.50").compareTo(remote.list(tenant).get(root.value()).lastReport().path("stat_cost").decimalValue()));
        assertTrue(rebuilds.excludesCurrentRow(tenant,account,"101","控成本测试计划"));
        assertFalse(rebuilds.excludesCurrentRow(tenant,account,"201","新计划"));
    }

    @Test void archivedSettingsAndLatestSharedMaterialsAreUsedWhenRestoring() {
        var volume=service.transfer(tenant,root,volume("restore-new-settings"));
        assertEquals("SUCCEEDED",volume.status(),volume.message());
        var shared=(ObjectNode)upstream.get("201").at("/multi_product_creative_list/0/video_material/0");
        shared.put("video_id","updated-video").put("video_cover_id","updated-cover");

        var settings=new PlanSettingsEditStore(jdbc,json,plans,transfers,new DataSourceTransactionManager(ds));
        var editor=planEditor(settings);
        var version=settings.state(local.get(root.value())).version();
        var update=new PlanSettingsRequest("修改后的控成本计划",BigDecimal.valueOf(650),new BigDecimal("5.20"),version);
        TenantContext.set(new TenantPrincipal(new UserId("editor"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        try {
            editor.settings(tenant,root,update);
            assertThrows(BizException.class,()->editor.settings(tenant,root,update));
        } finally { TenantContext.clear(); }
        assertEquals(List.of("DELETE:101","CREATE:SMART_BID_CONSERVATIVE"),writes);
        assertEquals(0,BigDecimal.valueOf(650).compareTo(upstream.get("201").at("/delivery_setting/budget").decimalValue()));
        assertEquals("修改后的控成本计划_转投",upstream.get("201").path("name").asText());

        var restored=service.enableExisting(tenant,root);
        assertEquals("SUCCEEDED",restored.status(),restored.message());
        assertEquals(root.value(),restored.planId());
        assertEquals("修改后的控成本计划",local.get(root.value()).name().text());
        var next=upstream.get(restored.qcPlanId());
        assertEquals(0,BigDecimal.valueOf(650).compareTo(next.at("/delivery_setting/budget").decimalValue()));
        assertEquals("updated-video",next.at("/multi_product_creative_list/0/video_material/0/video_id").asText());
        assertTrue(local.get(volume.planId()).transferArchived());
        assertEquals(1,deliveryCount());
        assertArchivedHistoryRetained(root,"101");
    }

    @Test void linkedEditFromReviewingPlanUpdatesArchivedBudgetAndScheduleWithoutMixingRoi() {
        var volume=service.transfer(tenant,root,volume("edit-reviewing-plan"));
        assertEquals("SUCCEEDED",volume.status(),volume.message());
        var plan=local.get(volume.planId());
        plan.apply(PlanStatus.PENDING_AUDIT);
        upstream.get("201").put("status","AUDIT");
        assertEquals(PlanStatus.PENDING_AUDIT,com.lynx.ad.modules.delivery.domain.service.PlanEditVersion.platformStatus(upstream.get("201")));
        var store=new PlanSettingsEditStore(jdbc,json,plans,transfers,new DataSourceTransactionManager(ds));
        var editor=planEditor(store);
        var end=today.plusDays(3);
        var update=new PlanSettingsRequest("更新计划_转投",BigDecimal.valueOf(700),null,store.state(plan).version(),"DATE_RANGE",end);
        TenantContext.set(new TenantPrincipal(new UserId("editor"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        try { editor.settings(tenant,plan.id(),update); }
        finally { TenantContext.clear(); }
        var archived=local.get(root.value());
        assertTrue(archived.transferArchived());
        assertEquals("更新计划",archived.name().text());
        assertEquals(0,BigDecimal.valueOf(700).compareTo(archived.setting().dailyBudget().amount()));
        assertEquals(end,archived.setting().schedule().endDate());
        assertEquals(end,plan.setting().schedule().endDate());
        assertEquals(new BigDecimal("4.96"),archived.setting().roi2Goal().orElseThrow().value());
        assertTrue(plan.setting().roi2Goal().isEmpty());
        assertArchivedHistoryRetained(root,"101");
    }

    @Test void receiptTimeoutCanBeVerifiedFromArchivedEditorWithoutResendingPlatformWrite() {
        var volume=service.transfer(tenant,root,volume("editable-receipt"));
        assertEquals("SUCCEEDED",volume.status());
        var store=new PlanSettingsEditStore(jdbc,json,plans,transfers,new DataSourceTransactionManager(ds));
        var editor=planEditor(store);
        var archived=local.get(root.value());
        var patch=new PlanSettingsRequest(null,BigDecimal.valueOf(680),null,store.state(archived).version());
        loseSettingsReceipt=true;
        TenantContext.set(new TenantPrincipal(new UserId("editor"),Optional.of(tenant),Optional.empty(),SysRole.USER));
        try {
            assertEquals(1406,assertThrows(BizException.class,()->editor.settings(tenant,root,patch)).code());
            assertEquals("UNCERTAIN",store.state(archived).status());
            assertEquals(0,BigDecimal.valueOf(500).compareTo(archived.setting().dailyBudget().amount()));
            var result=editor.verifySettings(tenant,root,new PlanManagementService.SettingsVerification(store.state(archived).version(),false));
            assertEquals("CONFIRMED",result.status());
            assertEquals("IDLE",store.state(archived).status());
            assertEquals(0,BigDecimal.valueOf(680).compareTo(archived.setting().dailyBudget().amount()));
            verify(management,times(1)).settings(credential,"201",null,BigDecimal.valueOf(680),null);
        } finally { TenantContext.clear(); }
    }

    private PlanManagementService planEditor(PlanSettingsEditStore settings) {
        var credentials=mock(com.lynx.ad.modules.identity.application.QianchuanCredentialFacade.class);
        when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);
        var editor=new PlanManagementService(plans,management,credentials,
                remote,mock(PlanQueryService.class),mock(BillingTrialService.class),mock(com.lynx.ad.modules.audit.application.AuditRecorder.class),jdbc,json);
        editor.settingsEdits(settings);
        editor.transferStore(transfers);
        org.springframework.test.util.ReflectionTestUtils.setField(editor,"transferRebuilds",rebuilds);
        return editor;
    }

    @Test void unavailableArchiveReportLeavesOriginalInstanceRunning() {
        when(management.insightReport(eq(credential),eq("101"),any(),any(),eq(true))).thenThrow(new BizException(1406,"报表暂不可用"));
        assertEquals("FAILED",service.transfer(tenant,root,volume("unavailable-report")).status());
        assertEquals(PlanStatus.DELIVERING,local.get(root.value()).status());
        assertTrue(writes.isEmpty());
    }

    private void assertArchivedHistoryRetained(PlanId id,String physicalId) {
        var records=rebuilds.reports(tenant,Set.of(id.value())).get(id.value());
        assertEquals(List.of(physicalId),records.stream().map(PlanTransferRebuildStore.ArchivedReport::qcPlanId).toList());
    }

    private PlanTransferRequest volume(String key) {
        return new PlanTransferRequest(key,"101","SMART_BID_CONSERVATIVE",new BigDecimal("50"),null);
    }

    private long deliveryCount() {
        return upstream.values().stream().filter(plan->"DELIVERY_OK".equals(plan.path("status").asText())).count();
    }

    private void saveReport(PlanId id,String cost,String gmv,String roi,int orders) {
        var metrics=json.createObjectNode().put("stat_cost",new BigDecimal(cost)).put("total_pay_order_gmv_include_coupon_for_roi2",new BigDecimal(gmv))
                .put("total_prepay_and_pay_order_roi2",new BigDecimal(roi)).put("total_pay_order_count_for_roi2",orders);
        remote.report(tenant,id.value(),metrics,null,today,Instant.now());
    }

    private void assertArchived(PlanId id,String previousQc,String cost) {
        AdPlan archived=local.get(id.value());
        assertTrue(archived.transferArchived());
        assertEquals(PlanStatus.PAUSED,archived.status());
        assertTrue(archived.qcPlanId().isEmpty());
        assertEquals(previousQc,service.history(tenant,id).getFirst().qcPlanId());
        assertEquals(0,new BigDecimal(cost).compareTo(service.history(tenant,id).getFirst().costYuan()));
        assertTrue(rebuilds.syncGuard(tenant,account).excludes(previousQc,null));
    }
}
