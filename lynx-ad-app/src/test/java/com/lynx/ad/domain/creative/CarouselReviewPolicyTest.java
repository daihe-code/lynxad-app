package com.lynx.ad.domain.creative;

import com.lynx.ad.modules.creative.domain.service.CarouselReviewPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarouselReviewPolicyTest {
    private CarouselReviewPolicy.Part part(String label,String status) {
        return new CarouselReviewPolicy.Part(label,status,List.of("不符合规范"),"审核服务不可用");
    }
    @Test void anyTextImageOrAudioRejectionBlocksWholeCompositeEvenWhileOthersArePending() {
        for(String rejected:List.of("文本","图片 1","图片 2","音频")) {
            var parts=List.of("文本","图片 1","图片 2","音频").stream().map(label->part(label,label.equals(rejected)?"REJECTED":"PENDING")).toList();
            var result=CarouselReviewPolicy.aggregate(parts);
            assertEquals("REJECTED",result.status());
            assertTrue(result.reasons().stream().anyMatch(r->r.startsWith(rejected+"：")));
        }
    }
    @Test void approvalRequiresEveryPartToHaveExplicitlyPassed() {
        for(String nonApproval:Arrays.asList(null,"QUEUED","RUNNING","PENDING","UNKNOWN")) {
            assertEquals("PENDING",CarouselReviewPolicy.aggregate(List.of(part("文本","APPROVED"),part("图片 1",nonApproval),part("音频","APPROVED"))).status());
        }
        assertEquals("PENDING",CarouselReviewPolicy.aggregate(List.of()).status());
        assertEquals("APPROVED",CarouselReviewPolicy.aggregate(List.of(part("文本","APPROVED"),part("图片 1","APPROVED"),part("音频","APPROVED"))).status());
    }
    @Test void technicalFailureAndManualReviewNeverCountAsPassing() {
        assertEquals("FAILED",CarouselReviewPolicy.aggregate(List.of(part("图片 1","APPROVED"),part("音频","FAILED"))).status());
        assertEquals("REJECTED",CarouselReviewPolicy.aggregate(List.of(part("图片 1","MANUAL_REVIEW"),part("音频","FAILED"))).status());
    }
}
