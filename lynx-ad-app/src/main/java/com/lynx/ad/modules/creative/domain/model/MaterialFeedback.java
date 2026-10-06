package com.lynx.ad.modules.creative.domain.model;

import com.lynx.ad.common.exception.BizException;
import java.util.List;

public record MaterialFeedback(List<String> reasons, String description) {
    public MaterialFeedback {
        if (reasons != null && (reasons.size() > 10 || reasons.stream().anyMatch(s -> s == null || s.length() > 50)))
            throw new BizException(1501, "素材操作原因不符合要求");
        reasons = reasons == null ? List.of() : reasons.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        description = description == null ? "" : description.trim();
        if (description.length() > 50) throw new BizException(1501, "素材操作说明最多 50 字");
    }

    public static MaterialFeedback empty() { return new MaterialFeedback(List.of(), ""); }

}
