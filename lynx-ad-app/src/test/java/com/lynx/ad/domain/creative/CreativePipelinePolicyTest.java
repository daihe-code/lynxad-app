package com.lynx.ad.domain.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.creative.domain.model.AdTitle;
import com.lynx.ad.modules.creative.domain.model.CreativeArtifact;
import com.lynx.ad.modules.creative.domain.model.CreativeMode;
import com.lynx.ad.modules.creative.domain.model.CreativePackage;
import com.lynx.ad.modules.creative.domain.model.CreativeTask;
import com.lynx.ad.modules.creative.domain.model.PipelineStep;
import com.lynx.ad.modules.creative.domain.model.StepName;
import com.lynx.ad.modules.creative.domain.model.StepStatus;
import com.lynx.ad.modules.creative.domain.model.TitleType;
import com.lynx.ad.modules.creative.domain.service.CreativePipelinePolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CreativePipelinePolicyTest {

    private final CreativePipelinePolicy policy = new CreativePipelinePolicy();

    @Test
    void reservedRejected() {
        assertThrows(DomainException.class, () -> policy.rejectReserved(CreativeMode.RESERVED));
    }

    @Test
    void stepOrder() {
        assertEquals(StepName.PARSE, policy.next(null));
        assertEquals(StepName.TITLE, policy.next(StepName.PARSE));
        assertEquals(StepName.DONE, policy.next(StepName.UPLOAD));
    }

    @Test
    void cannotSucceedFromRunningSkip() {
        CreativeTask task = CreativeTask.submitStandard(new TenantId("t1"), Optional.empty(), List.of(new QcProductId("p1")));
        task.start();
        CreativePackage pkg = CreativePackage.of(List.of(new CreativeArtifact(
                new QcProductId("p1"),
                List.of(new AdTitle("标题", TitleType.CUSTOM)),
                Optional.empty(),
                Optional.empty())));
        assertThrows(DomainException.class, () -> task.succeed(pkg));
        assertThrows(DomainException.class, () -> task.advance(new PipelineStep(StepName.VIDEO, StepStatus.SUCCEEDED, Optional.empty())));
    }
}
