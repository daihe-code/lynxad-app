package com.lynx.ad.modules.dataops.domain.port;

import com.lynx.ad.modules.dataops.domain.model.MetricSnapshot;

public record SnapshotPair(MetricSnapshot current, MetricSnapshot previous) {}
