package com.lynx.ad.modules.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.application.GenerationIssueMapper;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenerationIssueMapperTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void changedSelectionKeepsOriginalGroupOwnershipAndDropsRemovedProductFailures() throws Exception {
        var s=state();s.generationGroupProducts.put("group-1","123");s.generationGroupProducts.put("group-2","456");s.products.removeFirst();
        var job=json.readTree("{\"status\":\"failed\",\"groups\":[{\"group_id\":\"group-1\",\"status\":\"failed\",\"error\":\"移出商品失败\"},{\"group_id\":\"group-2\",\"status\":\"failed\",\"error\":\"保留商品失败\"}]}");
        var issues=GenerationIssueMapper.from(s,job);assertEquals(1,issues.size());assertEquals("456",issues.getFirst().productId());assertEquals("保留商品失败",issues.getFirst().message());
    }
    private CreationState state() {
        var s=new CreationState();s.generationProductIds=List.of("123","456");
        for(String id:s.generationProductIds)s.products.add(new CreationState.Product(id,id,"商品"+id,null,null,null,null,null,List.of(),List.of()));
        return s;
    }
    @Test void failedVideoIsAttachedToItsProductAndSlotWithActualReason() throws Exception {
        var issues=GenerationIssueMapper.from(state(),json.readTree("""
          {"status":"partial_failed","groups":[{"group_id":"group-2","status":"partial_failed","expected":{"videos":1,"product_cards":1},"materials":[
          {"material_id":"v2","material_type":"video","version":"v1","status":"failed","content":{"title":"通勤视频"},"detail":{"set_id":"video-1","error":"视频合成失败"}},
          {"material_id":"i2","material_type":"product_card","version":"v1","status":"succeeded"}],
          "generation_failures":[{"material_type":"video","slot_id":"video-1","message":"视频合成失败"}]}]}
          """));
        assertEquals(1,issues.size());var issue=issues.getFirst();assertEquals("456",issue.productId());assertEquals("VIDEO",issue.type());
        assertEquals("v2",issue.materialId());assertEquals("video-1",issue.slotId());assertEquals("通勤视频",issue.title());assertEquals("视频合成失败",issue.message());
    }
    @Test void componentFailureWithoutAFileKeepsSlotAndDeduplicatesComponents() throws Exception {
        var issues=GenerationIssueMapper.from(state(),json.readTree("""
          {"status":"failed","groups":[{"group_id":"group-1","status":"failed","expected":{"videos":1},"materials":[],
          "generation_failures":[{"material_type":"video","slot_id":"video-1","title":"视频创意甲","message":"视频生成超时"},
          {"material_type":"video","slot_id":"video-1","message":"封面生成失败"}]}]}
          """));
        assertEquals(1,issues.size());assertNull(issues.getFirst().materialId());assertEquals("VIDEO",issues.getFirst().type());
        assertTrue(issues.getFirst().message().contains("视频生成超时"));assertTrue(issues.getFirst().message().contains("封面生成失败"));
    }
    @Test void completedLatestVersionDoesNotShowPreviousFailure() throws Exception {
        var issues=GenerationIssueMapper.from(state(),json.readTree("""
          {"status":"succeeded","groups":[{"group_id":"group-1","status":"succeeded","expected":{"videos":1},"materials":[
          {"material_id":"new","material_type":"video","version":"v1","status":"succeeded"},
          {"material_id":"old","material_type":"video","version":"v2","status":"failed","detail":{"error":"历史失败"}}]}]}
          """));
        assertTrue(issues.isEmpty());
    }
    @Test void missingKnownSlotIsNotSilentlyDroppedAndRunningSlotsAreNotFailures() throws Exception {
        String groups="\"groups\":[{\"group_id\":\"group-1\",\"expected\":{\"videos\":1},\"materials\":[]}]";
        assertTrue(GenerationIssueMapper.from(state(),json.readTree("{\"status\":\"processing\","+groups+"}")).isEmpty());
        var issues=GenerationIssueMapper.from(state(),json.readTree("{\"status\":\"failed\","+groups+"}"));
        assertEquals(1,issues.size());assertEquals("VIDEO",issues.getFirst().type());assertNotNull(issues.getFirst().slotId());
    }
    @Test void unknownPlanningTotalsRemainProductLevelAndLegacyJsonStillLoads() throws Exception {
        var issues=GenerationIssueMapper.from(state(),json.readTree("""
          {"status":"failed","groups":[{"group_id":"group-1","status":"failed","error":{"message":"商品资料不完整"},"materials":[]}]}
          """));
        assertEquals(1,issues.size());assertNull(issues.getFirst().type());assertEquals("商品资料不完整",issues.getFirst().message());
        var legacy=json.readValue("{\"productId\":\"123\",\"productName\":\"商品\",\"message\":\"旧记录\"}",CreationState.GenerationIssue.class);
        assertNull(legacy.type());assertEquals("旧记录",legacy.message());
    }
    @Test void rejectedRegenerationOnlyMarksTheRequestedProductAndKeepsOriginalMaterial() {
        var s=state();var original=new CreationState.Material();original.id="original";original.productId="456";original.type="VIDEO";original.title="原视频";original.status="READY";s.materials.add(original);
        s.regeneration=new CreationState.Regeneration();s.regeneration.requestId="revision";s.regeneration.sourceId="original";
        var issues=GenerationIssueMapper.requested(s,"本次修订视频服务超时");
        assertEquals(1,issues.size());assertEquals("456",issues.getFirst().productId());assertEquals("VIDEO",issues.getFirst().type());
        assertNull(issues.getFirst().materialId());assertEquals("revision:revision",issues.getFirst().slotId());assertEquals("READY",original.status);
    }
}
