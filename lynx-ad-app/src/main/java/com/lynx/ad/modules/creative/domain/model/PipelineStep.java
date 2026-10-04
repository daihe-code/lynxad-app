package com.lynx.ad.modules.creative.domain.model;

import com.lynx.ad.common.domain.Ids.QcProductId;
import java.util.Optional;

public record PipelineStep(StepName name, StepStatus status, Optional<QcProductId> productId) {}
