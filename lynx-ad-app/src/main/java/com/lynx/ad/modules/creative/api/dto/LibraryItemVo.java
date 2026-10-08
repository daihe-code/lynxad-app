package com.lynx.ad.modules.creative.api.dto;

public record LibraryItemVo(
        String id,
        String type,
        String source,
        String filename,
        long sizeBytes,
        String imageMode,
        Integer durationSec,
        String category,
        String createdAt,
        boolean used,
        String reviewBadge,
        String materialId,
        String objectId,
        String posterUrl,
        String playUrl,
        Integer width,
        Integer height,
        String assetId,
        String storageStatus,
        java.util.List<com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync> syncs,
        com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Review aiReview,
        String carouselId) {
    public LibraryItemVo(String id,String type,String source,String filename,long sizeBytes,String imageMode,Integer durationSec,String category,String createdAt,boolean used,String reviewBadge,String materialId,String objectId,String posterUrl,String playUrl,Integer width,Integer height,String assetId,String storageStatus,java.util.List<com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync> syncs,com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Review aiReview) {
        this(id,type,source,filename,sizeBytes,imageMode,durationSec,category,createdAt,used,reviewBadge,materialId,objectId,posterUrl,playUrl,width,height,assetId,storageStatus,syncs,aiReview,null);
    }
    public LibraryItemVo(String id,String type,String source,String filename,long sizeBytes,String imageMode,Integer durationSec,String category,String createdAt,boolean used,String reviewBadge,String materialId,String objectId,String posterUrl,String playUrl,Integer width,Integer height,String assetId,String storageStatus,java.util.List<com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync> syncs) {
        this(id,type,source,filename,sizeBytes,imageMode,durationSec,category,createdAt,used,reviewBadge,materialId,objectId,posterUrl,playUrl,width,height,assetId,storageStatus,syncs,null);
    }
    public LibraryItemVo(String id,String type,String source,String filename,long sizeBytes,String imageMode,Integer durationSec,String category,String createdAt,boolean used,String reviewBadge,String materialId,String objectId,String posterUrl,String playUrl,Integer width,Integer height) {
        this(id,type,source,filename,sizeBytes,imageMode,durationSec,category,createdAt,used,reviewBadge,materialId,objectId,posterUrl,playUrl,width,height,null,null,java.util.List.of(),null);
    }
}
