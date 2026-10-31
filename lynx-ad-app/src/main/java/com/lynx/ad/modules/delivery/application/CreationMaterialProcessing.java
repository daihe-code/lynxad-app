package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.application.CarouselService;
import com.lynx.ad.modules.creative.application.CarouselPlatformService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.model.MaterialProcessing;
import java.util.*;

/** Reads durable asset/review/account-specific receipts. Never generates or uploads media. */
public class CreationMaterialProcessing {
    private final AssetUploadService assets;
    private final CarouselService carousels;
    private CarouselPlatformService carouselPlatform;
    public CreationMaterialProcessing(AssetUploadService assets,CarouselService carousels){this.assets=assets;this.carousels=carousels;}
    public void carouselPlatform(CarouselPlatformService value){carouselPlatform=value;}
    public record Inspection(MaterialProcessing progress,List<String> retryAssets) {}
    public Map<String,AssetUploadService.Detail> hydrate(TenantId tenant,CreationState state) {
        var cache=new HashMap<String,AssetUploadService.Detail>();
        for(var material:state.materials) {
            material.processing=inspect(tenant,state,material,cache).progress();
            var media="VIDEO".equals(material.type)?Collections.singletonList(material.videoAssetId):material.imageAssetIds;
            boolean generated=!media.isEmpty()&&media.stream().allMatch(id->{
                var asset=cache.get(id);
                return asset!=null&&("engine_oss".equals(asset.storageMode())
                        ||asset.storageMetadata()!=null&&"AI_GENERATED".equals(asset.storageMetadata().origin()));
            });
            material.source=generated||material.engineMaterialId!=null&&!material.engineMaterialId.isBlank()?"LYNX":"UPLOAD";
        }
        return cache;
    }
    public Inspection inspect(CreationState state,CreationState.Material material) {return inspect(new TenantId(state.tenantId),state,material,new HashMap<>());}
    private Inspection inspect(TenantId tenant,CreationState state,CreationState.Material material,Map<String,AssetUploadService.Detail> cache) {
        if(assets==null)return new Inspection(MaterialProcessing.legacy(material),List.of());
        var refs=new LinkedHashMap<String,String>();
        material.sourceUrls.keySet().forEach(key->refs.put(key,null));refs.putAll(material.importedAssets);
        refs.put("TEXT",material.textAssetId);
        var pool=state.titlePools.get(material.productId);
        if(pool!=null){String copy="IMAGE".equals(material.type)?pool.cardTextAssetId:pool.customTextAssetId;if(copy!=null)refs.put("TEXT",copy);}
        if("VIDEO".equals(material.type)){put(refs,"VIDEO",material.videoAssetId);put(refs,"COVER",material.coverAssetId);}
        if("CAROUSEL".equals(material.type))put(refs,"AUDIO",material.audioAssetId);
        for(int i=0;i<material.imageAssetIds.size();i++)put(refs,"IMAGE_"+i,material.imageAssetIds.get(i));
        if(!"VIDEO".equals(material.type)&&refs.keySet().stream().noneMatch(key->key.startsWith("IMAGE_")))refs.put("IMAGE_0",null);
        String bundleReview=null;
        if("CAROUSEL".equals(material.type)&&material.carouselId!=null&&carousels!=null) {
            try {
                var bundle=carousels.detail(tenant,material.carouselId);
                if(bundle!=null) {
                    bundleReview=bundle.aiReview()==null?"PENDING":bundle.aiReview().status();
                    for(var part:bundle.parts()) {
                        var key="IMAGE".equals(part.role())?"IMAGE_"+part.position():part.role();
                        put(refs,key,part.asset().id());cache.put(part.asset().id(),part.asset());
                    }
                }
            }catch(BizException unavailable){bundleReview="UNKNOWN";}
        }
        var components=new ArrayList<MaterialProcessing.Component>();var retry=new LinkedHashSet<String>();
        for(var entry:refs.entrySet()) {
            String key=entry.getKey(),ref=entry.getValue();
            if(ref==null||ref.isBlank()){components.add(new MaterialProcessing.Component(key,"PENDING","PENDING","PENDING",null));continue;}
            AssetUploadService.Detail asset;
            try {asset=cache.computeIfAbsent(ref,id->assets.detail(tenant,id));}
            catch(BizException unavailable){asset=null;}
            if(asset==null){components.add(new MaterialProcessing.Component(key,"UNKNOWN","UNKNOWN","UNKNOWN","素材记录暂不可读取"));continue;}
            String storage=switch(Objects.toString(asset.storageStatus(),"")) {
                case "STORED" -> "SUCCEEDED";case "FAILED","EXPIRED" -> "FAILED";
                case "WAITING","UPLOADING","VALIDATING" -> "RUNNING";default -> "UNKNOWN";
            };
            String review=review(asset.aiReview()==null?null:asset.aiReview().status(),"engine_oss".equals(asset.storageMode()));
            var receipt=asset.syncs().stream().filter(sync->Objects.equals(state.accountId,sync.accountId())).findFirst().orElse(null);
            String sync="TEXT".equals(key)?"NOT_REQUIRED":receipt==null?"PENDING":switch(receipt.status()) {
                case "UPLOADED" -> "REJECTED".equals(receipt.reviewStatus())?"REJECTED":receipt.objectId()!=null&&!receipt.objectId().isBlank()?"SUCCEEDED":"UNKNOWN";
                case "FAILED","UNCERTAIN","WAITING_AUTH","PAUSED","RUNNING" -> receipt.status();
                default -> "PENDING";
            };
            String message="FAILED".equals(storage)?asset.errorMessage():Set.of("REJECTED","FAILED").contains(review)
                    ?asset.aiReview().summary()==null?asset.aiReview().errorMessage():asset.aiReview().summary():receipt==null?null:receipt.verification()!=null?receipt.verification().message():receipt.errorMessage();
            components.add(new MaterialProcessing.Component(key,storage,review,sync,safe(message)));
            if(!"TEXT".equals(key)&&("FAILED".equals(sync)||"PAUSED".equals(sync)||receipt==null))retry.add(ref);
        }
        String storage=aggregate(components.stream().map(MaterialProcessing.Component::storageStatus).toList(),List.of("FAILED","UNKNOWN","RUNNING","PENDING"));
        String review=aggregate(components.stream().map(MaterialProcessing.Component::reviewStatus).toList(),List.of("REJECTED","FAILED","UNKNOWN","RUNNING","PENDING"));
        if(bundleReview!=null&&!Set.of("APPROVED","NOT_REQUIRED").contains(bundleReview))review=review(bundleReview,false);
        String sync=aggregate(components.stream().map(MaterialProcessing.Component::syncStatus).toList(),List.of("UNCERTAIN","REJECTED","WAITING_AUTH","FAILED","PAUSED","UNKNOWN","RUNNING","PENDING"));
        if("CAROUSEL".equals(material.type)&&material.carouselId!=null) {
            String packageSync="PENDING";
            if(carouselPlatform!=null) {
                try {
                    var receipt=carouselPlatform.receipt(tenant,material.carouselId,state.accountId);
                    packageSync=receipt==null?"UNKNOWN":switch(receipt.status()) {
                        case "SUCCEEDED" -> receipt.objectId()!=null&&!receipt.objectId().isBlank()?"SUCCEEDED":"UNKNOWN";
                        case "UNCERTAIN","SENDING" -> "UNCERTAIN";case "FAILED" -> "FAILED";default -> "PENDING";
                    };
                }catch(org.springframework.dao.DataAccessException unavailable){packageSync="UNKNOWN";}
            }else if("SYNC".equals(material.failureStage)&&Objects.equals(1523,material.failureCode))packageSync="UNCERTAIN";
            else if("READY".equals(material.status))packageSync="SUCCEEDED";
            components.add(new MaterialProcessing.Component("CAROUSEL","SUCCEEDED",review,packageSync,null));
            sync=aggregate(List.of(sync,packageSync),List.of("UNCERTAIN","REJECTED","WAITING_AUTH","FAILED","PAUSED","UNKNOWN","RUNNING","PENDING"));
        }
        String stage,message=null;
        if(!"SUCCEEDED".equals(storage)) {
            if("FAILED".equals(material.status)&&"REGISTRATION".equals(material.failureStage))storage="FAILED";
            stage="REGISTRATION";message="FAILED".equals(storage)?"产物已保留，素材登记失败，可重新检查登记状态。":"UNKNOWN".equals(storage)?"素材登记状态暂时无法核验。":"正在登记素材，尚未开始同步千川。";
        }else if(!"SUCCEEDED".equals(review)){stage="REVIEW";message=Set.of("REJECTED","FAILED").contains(review)?"素材质检未通过，请查看原因后处理。":"正在等待素材质检结果。";}
        else if(!"SUCCEEDED".equals(sync)){stage="SYNC";message=switch(sync) {
            case "UNCERTAIN" -> "千川上传结果待确认，请核对上传记录，不会自动重复上传。";
            case "WAITING_AUTH" -> "等待千川授权恢复，产物和已完成的上传均已保留。";
            case "REJECTED" -> "千川审核未通过，请处理平台拒绝原因。";
            case "FAILED" -> "千川同步失败，可只重试失败组件，无需重新生成。";
            case "PAUSED" -> "千川同步已暂停，请检查运行模式和平台配置。";
            case "UNKNOWN" -> "千川同步状态暂时无法核验。";
            default -> "正在同步千川，生成产物已保留。";
        };}
        else if(!"READY".equals(material.status)){stage="VALIDATION";message=material.error==null?"文件已同步，正在核验投放素材和标题。":safe(material.error);}
        else stage="READY";
        boolean canRetry="SUCCEEDED".equals(storage)&&"SUCCEEDED".equals(review)&&!retry.isEmpty()
                &&!Set.of("UNCERTAIN","WAITING_AUTH","REJECTED","UNKNOWN").contains(sync);
        return new Inspection(new MaterialProcessing(material.engineMaterialId==null?"NOT_REQUIRED":"SUCCEEDED",storage,review,sync,stage,message,canRetry,List.copyOf(components)),List.copyOf(retry));
    }
    private static void put(Map<String,String> refs,String key,String value){if(value!=null||!refs.containsKey(key))refs.put(key,value);}
    private static String review(String status,boolean engine) {
        return switch(Objects.toString(status,"")) {
            case "APPROVED" -> "SUCCEEDED";case "NOT_REQUIRED" -> engine?"NOT_REQUIRED":"UNKNOWN";
            case "REJECTED","MANUAL_REVIEW" -> "REJECTED";case "FAILED" -> "FAILED";
            case "RUNNING","PROCESSING" -> "RUNNING";case "UNKNOWN" -> "UNKNOWN";default -> "PENDING";
        };
    }
    private static String aggregate(List<String> values,List<String> priority) {
        for(String status:priority)if(values.contains(status))return status;
        return values.isEmpty()?"UNKNOWN":"SUCCEEDED";
    }
    private static String safe(String text) {
        if(text==null)return null;
        String clean=text.replaceAll("https?://\\S+","[服务地址]").replaceAll("[\\p{Cntrl}]"," ")
                .replaceAll("(?i)(access[_-]?token|refresh[_-]?token|api[_-]?key|secret|password)[\\s\"']*[:=][\\s\"']*[^\\s,;\"']+","$1=[已隐藏]");
        return clean.substring(0,Math.min(400,clean.length()));
    }
}
