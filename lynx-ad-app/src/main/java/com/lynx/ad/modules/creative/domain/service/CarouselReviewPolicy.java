package com.lynx.ad.modules.creative.domain.service;

import java.util.*;

/** A composite is eligible only when every component has explicitly passed review. */
public final class CarouselReviewPolicy {
    private CarouselReviewPolicy() {}
    public record Part(String label, String status, List<String> reasons, String error) {}
    public record Result(String status, String summary, List<String> reasons) {}
    public static Result aggregate(List<Part> parts) {
        List<String> reasons = new ArrayList<>();
        boolean rejected = false, failed = false, pending = parts.isEmpty();
        for (var part : parts) {
            if (Set.of("REJECTED", "MANUAL_REVIEW").contains(Objects.toString(part.status(), ""))) {
                rejected = true;
                reasons.add(part.label() + "质检不合格" + ("MANUAL_REVIEW".equals(part.status()) ? "（需人工复核）" : ""));
                if (part.reasons() != null) for (String reason : part.reasons()) reasons.add(part.label() + "：" + reason);
            } else if ("FAILED".equals(part.status())) {
                failed = true; reasons.add(part.label() + "质检失败：" + Objects.toString(part.error(), "请重试该组件质检"));
            } else if (!"APPROVED".equals(part.status())) pending = true;
        }
        if (rejected) return new Result("REJECTED", "图文不合格：至少一个组件质检不合格，整条图文不可使用或同步。", reasons);
        if (failed) return new Result("FAILED", "图文质检失败：尚未全部合格，整条图文不予放行。", reasons);
        if (pending) return new Result("PENDING", "图文质检中，文本、全部图片及音频均合格后才可使用或同步。", List.of());
        return new Result("APPROVED", "图文合格：文本、全部图片及音频均已通过质检。", List.of());
    }
}
