package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import org.junit.jupiter.api.Test;
import java.util.*;

class CreationCardSelectionTest {
    private CreationState.Material card(String id,String source,String family,String status) {
        var m=new CreationState.Material();m.id=id;m.type="IMAGE";m.productId="p";m.engineJobId="job";
        m.deliverySource=source;m.deliveryFamilyId=family;m.status=status;
        if("READY".equals(status))m.imageAssetIds.add("asset-"+id);
        return m;
    }
    @Test void twoGeneratedCardsReplacePhotoAlternativeInDraftAndInvalidateConfirmation() {
        var s=new CreationState();s.confirmedVersion=s.packageVersion;
        s.materials.addAll(List.of(card("base","product_reference","slot0","READY"),
                card("ai0","ai_generated","slot0","READY"),card("ai1","ai_generated","slot1","READY")));
        assertTrue(s.retireReferenceAlternatives());
        assertEquals(List.of("ai0","ai1"),s.materials.stream().map(m->m.id).toList());
        assertEquals(List.of("base"),s.removedMaterialIds);assertNull(s.confirmedVersion);
        assertEquals(2,s.packageVersion);assertFalse(s.retireReferenceAlternatives());
    }
    @Test void failedOrDifferentProductOrJobNeverRemovesOnlyUsablePhoto() {
        var s=new CreationState();s.materials.add(card("base","product_reference","slot0","READY"));
        s.materials.add(card("failed","ai_generated","slot0","FAILED"));
        var other=card("other","ai_generated","slot0","READY");other.productId="different";s.materials.add(other);
        var another=card("another","ai_generated","slot0","READY");another.engineJobId="other-job";s.materials.add(another);
        assertFalse(s.retireReferenceAlternatives());assertEquals(4,s.materials.size());
    }
    @Test void publishedOrPublishingSelectionsAreNeverChanged() {
        for(String status:List.of("QUEUED","PUBLISHING","SUCCEEDED")) {
            var s=new CreationState();s.publishStatus=status;
            s.materials.addAll(List.of(card("base","product_reference","slot0","READY"),card("ai","ai_generated","slot0","READY")));
            assertFalse(s.retireReferenceAlternatives());assertEquals(2,s.materials.size());
        }
    }
}
