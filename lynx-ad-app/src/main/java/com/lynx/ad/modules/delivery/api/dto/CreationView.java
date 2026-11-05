package com.lynx.ad.modules.delivery.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.model.ProductBasicAnalysis;
import com.lynx.ad.modules.delivery.domain.model.ProductAnalysisOutcome;
import com.lynx.ad.modules.delivery.domain.service.ProductBasicAnalysisService;
import com.lynx.ad.modules.delivery.domain.model.MaterialProcessing;
import com.lynx.ad.modules.delivery.domain.service.CreationSyncRecovery;
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
        String qcPlanId, String publishError, List<AdditionView> generationAdditions,
        ProductBasicAnalysis basicAnalysis, long analysisStartedAt, String generationResultStatus, long regenerationRetryAt,
        java.util.Map<String,TitlePoolView> titlePools,String publishRecovery,String groupingDecision,MaterialEditView materialEdit,
        List<String> generationSelection,int reachedStep,List<String> generationPendingProductIds,CreationState.SelectionChange selectionChange,
        ContentEditView contentEdit,String contentEditDraftId,boolean productSelectionLocked,
        List<MaterialVersionView> materialVersions,RevisionView materialRevision,TitleGenerationView titleGeneration,MaterialOutcome materialOutcome,Boolean publishedMaterialsCurrent) {

    public static CreationView of(CreationState s) {
        var names=com.lynx.ad.modules.delivery.domain.service.MaterialDisplayNames.of(s);
        boolean failedAnalysis="SUCCEEDED".equals(s.analysisStatus)&&ProductAnalysisOutcome.failed(s.analysis);
        return new CreationView(s.planId, s.accountId, s.awemeId, s.name, s.revision, s.step,
                s.packageVersion, s.confirmedVersion, s.smartBidType, s.budget, s.roi2Goal,
                s.scheduleType, s.startDate, s.endDate, s.hideInAweme, List.copyOf(s.products),
                failedAnalysis?"FAILED":s.analysisStatus, failedAnalysis?ProductAnalysisOutcome.failureMessage(s.analysis):s.analysisError, s.analysis, s.recommendations,
                s.generationStatus, customerMessage(s), s.progress,
                List.copyOf(s.generationProductIds), s.generationStage, List.copyOf(s.generationCounts),
                s.generationIssues.stream().filter(i->!systemIssue(s,i.materialId())).toList(), s.activeMaterials().stream().map(material->MaterialView.of(material,names.get(material.id))).toList(),
                s.publishStatus, s.publishKey, s.qcPlanId, s.publishError,
                s.generationAdditions.stream().map(r->AdditionView.of(r,systemIssue(s,r.materialId))).toList(),
                ProductBasicAnalysisService.analyze(s.products), s.analysisStartedAt, MaterialProcessing.generationResult(s),s.regenerationRetryAt,titlePools(s),s.publishRecovery,s.groupingDecision,s.materialEdit==null?null:new MaterialEditView(s.materialEdit.status,s.materialEdit.message),
                s.generationSelection==null?null:List.copyOf(s.generationSelection),s.reachedStep(),List.copyOf(s.generationPendingProductIds),s.selectionChange,
                s.publishedEdit==null?null:new ContentEditView(s.publishedEdit.targetPlanId,s.publishedEdit.status,s.publishedEdit.message,s.publishedEdit.planStatus),s.publishedEditDraftId,
                com.lynx.ad.modules.delivery.domain.service.CreationProductSelection.locked(s),
                s.materials.stream().filter(CreationState.Material::pendingVersion).map(m->new MaterialVersionView(m.reviewSourceId,MaterialView.of(m,names.get(m.id)),m.versionDecision)).toList(),
                s.regeneration==null?null:new RevisionView(s.regeneration.requestId,s.regeneration.sourceId,s.regeneration.resultMaterialId,s.regeneration.status),
                s.titleGeneration==null?null:new TitleGenerationView(s.titleGeneration.status,s.titleGeneration.message,s.titleGeneration.requestId,s.titleGeneration.productId,s.titleGeneration.kind,s.titleGeneration.addition),outcome(s),s.publishedMaterialsCurrent);
    }
    private static boolean systemIssue(CreationState s,String id){return id!=null&&s.materials.stream().anyMatch(m->id.equals(m.id)&&CreationSyncRecovery.systemIssue(m));}
    private static String customerMessage(CreationState s){return s.materials.stream().anyMatch(CreationSyncRecovery::systemIssue)?s.needsMaterialRecovery()?"素材处理中":"部分素材暂未完成":s.generationMessage;}
    public record MaterialOutcome(long ready,long recovering,long failed,boolean generatedFailure) {}
    private static MaterialOutcome outcome(CreationState s){
        return new MaterialOutcome(s.activeMaterials().stream().filter(m->"READY".equals(m.status)&&(m.processing==null||"READY".equals(m.processing.stage()))).count(),
                s.activeMaterials().stream().filter(m->!"READY".equals(m.status)&&CreationSyncRecovery.systemIssue(m)).count(),
                s.materials.stream().filter(m->!m.pendingVersion()&&m.excludedFromDelivery).count(),
                s.materials.stream().anyMatch(m->!m.pendingVersion()&&m.excludedFromDelivery&&(m.engineMaterialId!=null||"LYNX".equals(m.source))));
    }
    public record MaterialVersionView(String sourceId,MaterialView material,String decision) {}
    public record RevisionView(String requestId,String sourceId,String materialId,String status) {}
    public record ContentEditView(String targetPlanId,String status,String message,String planStatus) {}
    public record MaterialEditView(String status,String message) {}
    public record TitlePoolView(List<String> customTitles,List<String> cardTitles,List<String> customGeneratedTitles,List<String> cardGeneratedTitles) {}
    public record TitleGenerationView(String status,String message,String requestId,String productId,String kind,boolean addition) {}
    private static java.util.Map<String,TitlePoolView> titlePools(CreationState s) {
        var result=new java.util.LinkedHashMap<String,TitlePoolView>();
        for(var product:s.products){var pool=com.lynx.ad.modules.delivery.domain.service.ProductTitlePool.of(s,product.qcProductId());result.put(product.qcProductId(),new TitlePoolView(List.copyOf(pool.customTitles),List.copyOf(pool.cardTitles),List.copyOf(pool.customGeneratedTitles),List.copyOf(pool.cardGeneratedTitles)));}
        return java.util.Map.copyOf(result);
    }
    public record AdditionView(String id,String productId,String type,String description,String status,
                               String createdAt,String materialId,String error) {
        static AdditionView of(CreationState.GenerationAddition r,boolean systemIssue){return new AdditionView(r.id,r.productId,r.type,r.description,r.status,r.createdAt,r.materialId,systemIssue?null:r.error);}
    }

    public record MaterialView(String id, String productId, String type, String title, String text,
            String status, String error, String previewUrl, String posterUrl,
            String engineMaterialId, List<String> titles, List<String> imageUrls,
            String generationRequestId,String generationDescription,JsonNode creativeProfile,String creativeProfileStatus,
            String deliverySource,String deliveryMode,String deliveryFamilyId,String typographyStatus, MaterialProcessing processing,String displayName,
            Integer width,Integer height,Double durationSec,String source,String addedVia) {
        static MaterialView of(CreationState.Material m,String displayName) {
            boolean systemIssue=CreationSyncRecovery.systemIssue(m);
            return new MaterialView(m.id, m.productId, m.type, m.title, m.text, systemIssue&&!m.excludedFromDelivery?"PROCESSING":m.status, systemIssue?null:m.error,
                    m.previewUrl, m.posterUrl, m.engineMaterialId, List.copyOf(m.titles),
                    List.copyOf(m.imageUrls),m.generationRequestId,m.generationDescription,m.creativeProfile,m.creativeProfileStatus,
                    m.deliverySource,m.deliveryMode,m.deliveryFamilyId,m.typographyStatus,CreationSyncRecovery.customerProgress(m),displayName,
                    m.width,m.height,m.durationSec,m.source!=null?m.source:m.engineMaterialId!=null?"LYNX":"UPLOAD",m.addedVia);
        }
    }
}
