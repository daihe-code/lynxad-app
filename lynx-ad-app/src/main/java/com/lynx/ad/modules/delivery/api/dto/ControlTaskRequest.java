package com.lynx.ad.modules.delivery.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ControlTaskRequest(String kind, String name, BigDecimal budget, BigDecimal hours,
                                 BigDecimal roi2Goal, List<String> materialIds, String requestKey) {}
