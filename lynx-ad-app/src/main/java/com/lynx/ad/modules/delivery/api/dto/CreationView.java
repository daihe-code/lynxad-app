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
        String qcPlanId, String publishError, List<AdditionView> generationAdditions) {

    public static CreationView of(CreationState s) {
        return new CreationView(s.planId, s.accountId, s.awemeId, s.name, s.revision, s.step,
                s.packageVersion, s.confirmedVersion, s.smartBidType, s.budget, s.roi2Goal,
                s.scheduleType, s.startDate, s.endDate, s.hideInAweme, List.copyOf(s.products),
                s.analysisStatus, s.analysisError, s.analysis, s.recommendations,
                s.generationStatus, s.generationMessage, s.progress,
                List.copyOf(s.generationProductIds), s.generationStage, List.copyOf(s.generationCounts),
                List.copyOf(s.generationIssues), s.materials.stream().map(MaterialView::of).toList(),
                s.publishStatus, s.publishKey, s.qcPlanId, s.publishError,
                s.generationAdditions.stream().map(AdditionView::of).toList());
    }
    public record AdditionView(String id,String productId,String type,String description,String status,
                               String createdAt,String materialId,String error) {
        static AdditionView of(CreationState.GenerationAddition r){return new AdditionView(r.id,r.productId,r.type,r.description,r.status,r.createdAt,r.materialId,r.error);}
    }

    public record MaterialView(String id, String productId, String type, String title, String text,
            String status, String error, String previewUrl, String posterUrl,
            String engineMaterialId, List<String> titles, List<String> imageUrls,
            String generationRequestId,String generationDescription,JsonNode creativeProfile,String creativeProfileStatus) {
        static MaterialView of(CreationState.Material m) {
            return new MaterialView(m.id, m.productId, m.type, m.title, m.text, m.status, m.error,
                    m.previewUrl, m.posterUrl, m.engineMaterialId, List.copyOf(m.titles),
                    List.copyOf(m.imageUrls),m.generationRequestId,m.generationDescription,m.creativeProfile,m.creativeProfileStatus);
        }
    }
}
