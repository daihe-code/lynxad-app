package com.lynx.ad.modules.creative.api.dto;

import java.util.List;

public record TaskVo(
        String taskId, String status, List<String> steps, String packageId, String errorMsg, List<PackageItemVo> items) {
    public TaskVo(String taskId, String status, List<String> steps, String packageId, String errorMsg) {
        this(taskId, status, steps, packageId, errorMsg, List.of());
    }
}
