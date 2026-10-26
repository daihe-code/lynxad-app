package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.CreationProductSelection;
import java.util.*;
import org.junit.jupiter.api.Test;

class CreationProductSelectionTest {
    CreationState state() {
        var s=new CreationState();s.products=new ArrayList<>(List.of(ProductBasicAnalysisTest.valid("123"),ProductBasicAnalysisTest.valid("456")));
        s.enginePlanId="original-task";s.engineJobId="original-job";s.generationStatus="SUCCEEDED";
        s.generationSelection=new ArrayList<>(List.of("123","456"));
        for(var p:s.products){var m=new CreationState.Material();m.id="material-"+p.qcProductId();m.productId=p.qcProductId();m.engineJobId=s.engineJobId;m.engineMaterialId=m.id;m.groupId="123".equals(p.qcProductId())?"group-1":"group-2";m.status="READY";m.previewUrl="https://assets.test/"+m.id;s.materials.add(m);s.titlePools.put(p.qcProductId(),new CreationState.TitlePool());}
        return s;
    }
    @Test void addingProductsPreservesAllExistingMaterialsAndTheirGenerationOwnership() {
        var s=state();var selected=new ArrayList<>(s.products);selected.add(ProductBasicAnalysisTest.valid("789"));
        CreationProductSelection.apply(s,selected);
        assertEquals(2,s.materials.size());assertEquals("https://assets.test/material-123",s.materials.getFirst().previewUrl);
        assertTrue(s.materials.stream().allMatch(m->"original-task".equals(m.enginePlanId)));
        assertEquals(List.of("789"),s.generationPendingProductIds);assertEquals(2,s.selectionChange.retainedMaterials());
        assertEquals(0,s.selectionChange.removedMaterials());assertEquals(2,s.titlePools.size());assertNull(s.engineJobId);
    }
    @Test void removingOrReplacingAProductPrunesOnlyItsPreviewAndPreventsOldResultsReturning() throws Exception {
        for(boolean replace:List.of(false,true)) {
            var s=state();var next=new ArrayList<>(List.of(s.products.get(1)));if(replace)next.add(ProductBasicAnalysisTest.valid("789"));
            CreationProductSelection.apply(s,next);
            s=new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(s),CreationState.class);
            assertEquals(List.of("456"),s.materials.stream().map(m->m.productId).toList());
            assertEquals(Set.of("456"),s.titlePools.keySet());assertTrue(s.removedMaterialIds.contains("material-123"));
            assertEquals(1,s.selectionChange.removedMaterials());assertEquals(replace?List.of("789"):List.of(),s.generationPendingProductIds);
            assertEquals("group-2",s.materials.getFirst().groupId);assertEquals("original-task",s.materials.getFirst().enginePlanId);
        }
    }
    @Test void reorderingTheSameProductsDoesNotResetOrQueueGeneration() {
        var s=state();CreationProductSelection.apply(s,List.of(s.products.get(1),s.products.getFirst()));
        assertEquals("SUCCEEDED",s.generationStatus);assertEquals("original-job",s.engineJobId);assertNull(s.selectionChange);assertTrue(s.generationPendingProductIds.isEmpty());
    }
    @Test void runningGenerationRejectsProductChangesWithoutTouchingPurchasedWork() throws Exception {
        var s=state();s.generationStatus="RUNNING";s.generationProductIds=new ArrayList<>(List.of("123","456"));
        var json=new ObjectMapper();String before=json.writeValueAsString(s);
        assertThrows(com.lynx.ad.common.exception.BizException.class,()->CreationProductSelection.apply(s,List.of(s.products.get(1),ProductBasicAnalysisTest.valid("789"))));
        assertEquals(before,json.writeValueAsString(s));
    }
    @Test void oneUnfinishedMaterialOrAdditionLocksSelectionEvenAfterTheBatchSummaryEnds() {
        var s=state();s.generationStatus="FAILED";s.materials.getFirst().status="PROCESSING";
        assertTrue(CreationProductSelection.locked(s));s.materials.getFirst().status="READY";
        assertFalse(CreationProductSelection.locked(s));
        var addition=new CreationState.GenerationAddition();addition.status="RUNNING";s.generationAdditions.add(addition);
        assertTrue(CreationProductSelection.locked(s));addition.status="FAILED";
        assertFalse(CreationProductSelection.locked(s));
        s.regeneration=new CreationState.Regeneration();s.regeneration.status="CONFIRMED";
        assertTrue(CreationProductSelection.locked(s));s.regeneration.status="SUCCEEDED";
        assertFalse(CreationProductSelection.locked(s));
    }
}
