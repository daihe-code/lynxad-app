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
    public boolean analysisRefreshRequested;
    public JsonNode analysis, groups, recommendations;
    /** Latest verified shared bindings, separate from immutable publish-attempt audit payloads. */
    public JsonNode verifiedPlatformCreatives, verifiedPlatformChannels;
    public String generationStatus = "NONE", generationMessage, engineTerminalStatus, pendingRegenerationId;
    public int progress;
    public String generationStage;
    public List<GenerationCount> generationCounts = new ArrayList<>();
    public List<GenerationIssue> generationIssues = new ArrayList<>();
    public record GenerationCount(String type,int completed,Integer total) {}
    public record GenerationIssue(String productId,String productName,String message) {}
    public List<String> generationProductIds = new ArrayList<>();
    public String pendingAdditionId;
    public List<GenerationAddition> generationAdditions = new ArrayList<>();
    public static class GenerationAddition {
        public String id,productId,type,description,status,createdAt,engineJobId,materialId,error;
        public JsonNode engineRequest;
    }
    public List<Material> materials = new ArrayList<>();
    public List<String> removedMaterialIds = new ArrayList<>();
    public List<FeedbackEvent> feedback = new ArrayList<>();
    public record FeedbackEvent(String materialId,String action,List<String> reasons,String description,String userId,String at) {}
    public String publishStatus = "NONE", publishKey, qcPlanId, publishError;
    public record Product(String productId, String qcProductId, String name, BigDecimal price,
                          BigDecimal userPrice, String imageUrl, String category, Long stock,
                          List<String> tags, List<String> unavailableReasons,
                          com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice priceInfo, String channelId) {
        public Product(String productId,String qcProductId,String name,BigDecimal price,BigDecimal userPrice,
                       String imageUrl,String category,Long stock,List<String> tags,List<String> unavailableReasons) {
            this(productId,qcProductId,name,price,userPrice,imageUrl,category,stock,tags,unavailableReasons,null,null);
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
