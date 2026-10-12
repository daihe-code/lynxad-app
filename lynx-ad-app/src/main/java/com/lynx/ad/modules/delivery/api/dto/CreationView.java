package com.lynx.ad.modules.delivery.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.math.BigDecimal;
import java.util.List;

/** Browser-facing state; worker receipts, engine download URLs and feedback stay on the server. */
public record CreationView(
        String planId, String accountId, String awemeId, String name, int revision, int step,
        int packageVersion, Integer confirmedVersion, String smartBidType, BigDecimal budget,
        BigDecimal roi2Goal, String scheduleType, String startDate, String endDate, boolean hideInAweme,
        List<CreationState.Product> products, String analysisStatus, String analysisError,
        JsonNode analysis, JsonNode recommendations, String generationStatus,
        String generationMessage, int progress, List<String> generationProductIds,
        String generationStage, List<CreationState.GenerationCount> generationCounts,
        List<CreationState.GenerationIssue> generationIssues,
        List<MaterialView> materials, String publishStatus, String publishKey,
        String qcPlanId, String publishError) {

    public static CreationView of(CreationState s) {
        return new CreationView(s.planId, s.accountId, s.awemeId, s.name, s.revision, s.step,
                s.packageVersion, s.confirmedVersion, s.smartBidType, s.budget, s.roi2Goal,
                s.scheduleType, s.startDate, s.endDate, s.hideInAweme, List.copyOf(s.products),
                s.analysisStatus, s.analysisError, s.analysis, s.recommendations,
                s.generationStatus, s.generationMessage, s.progress,
                List.copyOf(s.generationProductIds), s.generationStage, List.copyOf(s.generationCounts),
                List.copyOf(s.generationIssues), s.materials.stream().map(MaterialView::of).toList(),
                s.publishStatus, s.publishKey, s.qcPlanId, s.publishError);
    }

    public record MaterialView(String id, String productId, String type, String title, String text,
            String status, String error, String previewUrl, String posterUrl,
            String engineMaterialId, List<String> titles, List<String> imageUrls) {
        static MaterialView of(CreationState.Material m) {
            return new MaterialView(m.id, m.productId, m.type, m.title, m.text, m.status, m.error,
                    m.previewUrl, m.posterUrl, m.engineMaterialId, List.copyOf(m.titles),
                    List.copyOf(m.imageUrls));
        }
    }
}
