package com.lynx.ad.modules.creative.domain.model;

import com.lynx.ad.common.domain.Ids.PackageId;

public record RegenerationTarget(PackageId packageId, MaterialType type, int version) {}
