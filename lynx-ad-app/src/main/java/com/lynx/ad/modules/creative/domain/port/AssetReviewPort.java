package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.modules.creative.domain.model.AssetKind;
import java.util.List;

/** Actual AI-engine decisions; a successful HTTP response alone never grants approval. */
public interface AssetReviewPort {
    record Result(String status, String reviewId, String decision, String summary, List<String> reasons, String error) {}
    Result submit(AssetKind kind, String materialUrl, String textContent, String tenantId, String assetId);
    Result poll(AssetKind kind, String reviewId);
}
