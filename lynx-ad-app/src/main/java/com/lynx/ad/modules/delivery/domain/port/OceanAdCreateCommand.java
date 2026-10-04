package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Money;

public record OceanAdCreateCommand(String planId, String name, String smartBidType, Money budget) {}
