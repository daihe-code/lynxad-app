package com.lynx.ad.modules.delivery.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;

/** Durable wizard state. Media bytes and account-specific platform IDs remain in creative assets. */
public class CreationState {
    public String planId, tenantId, userId, accountId, awemeId, name;
    public int revision = 1, step = 1, packageVersion = 1;
    public Integer confirmedVersion;
    public String smartBidType = "CUSTOM", scheduleType = "FROM_NOW", startDate, endDate;
    public BigDecimal budget, roi2Goal;
    public boolean hideInAweme = true;
    public List<Product> products = new ArrayList<>();
    public String analysisStatus = "NONE", analysisError, enginePlanId, engineJobId, previousEngineJobId;
    public ProductBasicAnalysis basicAnalysis;
    public String analysisRequestId;
    public long analysisStartedAt;
    public boolean analysisRefreshRequested;
    public JsonNode analysis, groups, recommendations;
    public JsonNode analysisConfirmation;
    public String groupingDecision;
    /** Latest verified shared bindings, separate from immutable publish-attempt audit payloads. */
    public JsonNode verifiedPlatformCreatives, verifiedPlatformChannels;
    public String generationStatus = "NONE", generationMessage, engineTerminalStatus, engineTerminalJobId, pendingRegenerationId;
    public long regenerationRetryAt;
    /** Internal dispatch receipt. A source job ID alone never proves a revision was accepted. */
    public Regeneration regeneration;
    public GenerationRetry generationRetry;
    public static class GenerationRetry { public String requestId,type,status; }
    public static class Regeneration {
        public String requestId,sourceId,parentId,jobId,planTaskId,groupId,target,instruction;
        public String status="RESERVED",revisionId,executionJobId,resultMaterialId;
        public long sentAt;
    }
    public int generationReconciliationAttempts;
    public long generationReconciledAt;
    /** Terminal engine summaries can precede the last resumed component. Reads only; never resubmit. */
    public boolean hasFailedGenerationSummary() {
        return "FAILED".equals(generationStatus) && engineJobId!=null && !engineJobId.isBlank()
                && Set.of("partial_failed","failed").contains(Objects.toString(engineTerminalStatus,""))
                && Set.of("NONE","FAILED").contains(publishStatus) && pendingAdditionId==null;
    }
    public boolean needsGenerationReconciliation() { return hasFailedGenerationSummary()&&generationReconciliationAttempts<10; }
    public void resetGenerationReconciliation() { generationReconciliationAttempts=0;generationReconciledAt=0; }
    public int progress;
    public String generationStage;
    public List<GenerationCount> generationCounts = new ArrayList<>();
    public List<GenerationIssue> generationIssues = new ArrayList<>();
    public record GenerationCount(String type,int completed,Integer total,int failed) {
        public GenerationCount(String type,int completed,Integer total){this(type,completed,total,0);}
    }
    public record GenerationIssue(String productId,String productName,String message,String type,String materialId,String title,String slotId) {
        public GenerationIssue(String productId,String productName,String message){this(productId,productName,message,null,null,null,null);}
    }
    public List<String> generationProductIds = new ArrayList<>();
    public String pendingAdditionId;
    public List<GenerationAddition> generationAdditions = new ArrayList<>();
    public static class GenerationAddition {
        public String id,productId,type,description,status,createdAt,engineJobId,materialId,error;
        public JsonNode engineRequest;
    }
    public List<Material> materials = new ArrayList<>();
    public Map<String,TitlePool> titlePools = new LinkedHashMap<>();
    public static class TitlePool {
        public List<String> customTitles=new ArrayList<>(), cardTitles=new ArrayList<>();
        public String customTextAssetId,cardTextAssetId;
    }
    public List<String> removedMaterialIds = new ArrayList<>();
    public List<FeedbackEvent> feedback = new ArrayList<>();
    public record FeedbackEvent(String materialId,String action,List<String> reasons,String description,String userId,String at) {}
    public String publishStatus = "NONE", publishKey, qcPlanId, publishError,publishRecovery;
    public record DraftReference(String planId,String planName) {}
    public record Product(String productId, String qcProductId, String name, BigDecimal price,
                          BigDecimal userPrice, String imageUrl, String category, Long stock,
                          List<String> tags, List<String> unavailableReasons,
                          com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice priceInfo, String channelId, DraftReference draft, Long sales) {
        public Product(String productId,String qcProductId,String name,BigDecimal price,BigDecimal userPrice,
                       String imageUrl,String category,Long stock,List<String> tags,List<String> unavailableReasons,
                       com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice priceInfo,String channelId,DraftReference draft) {
            this(productId,qcProductId,name,price,userPrice,imageUrl,category,stock,tags,unavailableReasons,priceInfo,channelId,draft,null);
        }
        public Product(String productId,String qcProductId,String name,BigDecimal price,BigDecimal userPrice,
                       String imageUrl,String category,Long stock,List<String> tags,List<String> unavailableReasons,
                       com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice priceInfo,String channelId) {
            this(productId,qcProductId,name,price,userPrice,imageUrl,category,stock,tags,unavailableReasons,priceInfo,channelId,null);
        }
        public Product(String productId,String qcProductId,String name,BigDecimal price,BigDecimal userPrice,
                       String imageUrl,String category,Long stock,List<String> tags,List<String> unavailableReasons) {
            this(productId,qcProductId,name,price,userPrice,imageUrl,category,stock,tags,unavailableReasons,null,null);
        }
        public Product withDraft(DraftReference usage) {
            return new Product(productId,qcProductId,name,price,userPrice,imageUrl,category,stock,tags,unavailableReasons,priceInfo,channelId,usage,sales);
        }
    }
    public static class Material {
        public String id, productId, groupId, type, title, text, engineMaterialId, engineJobId;
        public String generationRequestId,generationDescription;
        public JsonNode creativeProfile;
        public String creativeProfileStatus;
        public String deliverySource, deliveryMode, deliveryFamilyId, typographyStatus;
        public String videoAssetId, coverAssetId, textAssetId, audioAssetId, carouselId;
        public List<String> imageAssetIds = new ArrayList<>();
        public String status = "PROCESSING", error, previewUrl, posterUrl;
        public String failureStage;
        public Integer failureCode;
        @com.fasterxml.jackson.annotation.JsonIgnore public MaterialProcessing processing;
        public List<String> imageUrls = new ArrayList<>();
        public String imageMode;
        public List<String> titles = new ArrayList<>();
        public String replacesId;
        public Map<String,String> sourceUrls = new LinkedHashMap<>();
        public Map<String,String> importedAssets = new LinkedHashMap<>();
    }
    public void changed() { revision++; confirmedVersion = null; }
    public void mediaChanged() { packageVersion++; changed(); }

    /** Retire old photo alternatives only after their generated sibling is usable. */
    public boolean retireReferenceAlternatives() {
        if(!Set.of("NONE","FAILED").contains(publishStatus)||qcPlanId!=null)return false;
        var families=new HashSet<List<String>>();
        for(var m:materials)if("IMAGE".equals(m.type)&&"READY".equals(m.status)
                &&"ai_generated".equals(m.deliverySource)&&m.deliveryFamilyId!=null&&!m.deliveryFamilyId.isBlank()
                &&m.engineJobId!=null&&!m.imageAssetIds.isEmpty())
            families.add(Arrays.asList(m.engineJobId,m.productId,m.deliveryFamilyId));
        boolean changed=materials.removeIf(m->{
            boolean retire="IMAGE".equals(m.type)&&"product_reference".equals(m.deliverySource)
                    &&families.contains(Arrays.asList(m.engineJobId,m.productId,m.deliveryFamilyId));
            if(retire&&!removedMaterialIds.contains(m.id))removedMaterialIds.add(m.id);
            return retire;
        });
        if(changed)mediaChanged();
        return changed;
    }
}
