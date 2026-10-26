package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CreationPayloadBuilder {
    private final ObjectMapper json;
    private final AssetUploadService assets;
    private final CarouselPlatformService carousels;
    private final CreationQianchuanGateway gateway;
    public CreationPayloadBuilder(ObjectMapper json,AssetUploadService assets,CarouselPlatformService carousels,CreationQianchuanGateway gateway){this.json=json;this.assets=assets;this.carousels=carousels;this.gateway=gateway;}
    public static void settings(CreationState s) {
        if(s.publishedEdit!=null)return;
        var mode=DeliveryInputPolicy.mode(s.smartBidType);
        DeliveryInputPolicy.budget(s.budget,mode,DeliveryInputPolicy.RULES.creation());
        if(mode==SmartBidType.CUSTOM)DeliveryInputPolicy.roi(s.roi2Goal,DeliveryInputPolicy.RULES.creation());
        DeliveryInputPolicy.schedule(s.scheduleType,s.startDate,s.endDate);
        DeliveryInputPolicy.name(s.name);
    }
    public AssetUploadService.Detail approved(CreationState s,String id,String kind) {
        if(id==null||id.isBlank())throw new BizException(1503,"商品素材尚未补齐");
        var a=assets.detail(new TenantId(s.tenantId),id);
        boolean ready=a.aiReview()!=null&&("APPROVED".equals(a.aiReview().status())||("engine_oss".equals(a.storageMode())&&"NOT_REQUIRED".equals(a.aiReview().status())));
        if(!kind.equals(a.kind())||!"STORED".equals(a.storageStatus())||!ready)throw new BizException(1503,"上传素材尚未审核通过，或引擎素材引用尚未就绪");
        return a;
    }
    public Sync uploaded(CreationState s,String id,String kind) {
        var a=approved(s,id,kind);
        return a.syncs().stream().filter(x->s.accountId.equals(x.accountId())&&"UPLOADED".equals(x.status())&&!"REJECTED".equals(x.reviewStatus())&&x.objectId()!=null&&!x.objectId().isBlank()).findFirst().orElseThrow(()->new BizException(1503,"素材尚未同步所选千川账户或已被平台拒绝"));
    }
    public void materialReady(CreationState s,CreationState.Material m) {
        var pool=s.titlePools.get(m.productId);
        var copy=pool==null?m.titles:"IMAGE".equals(m.type)?pool.cardTitles:pool.customTitles;
        if(copy.isEmpty())copy=m.titles;
        String textId=pool==null?null:"IMAGE".equals(m.type)?pool.cardTextAssetId:pool.customTextAssetId;
        try {DeliveryInputPolicy.titles(copy,false);}
        catch(BizException invalid){throw new BizException(1520,invalid.getMessage());}
        approved(s,textId==null?m.textAssetId:textId,"TEXT");
        switch(m.type) {
            case "VIDEO" -> {uploaded(s,m.videoAssetId,"VIDEO");uploaded(s,m.coverAssetId,"IMAGE");}
            case "IMAGE" -> {if(m.imageAssetIds.isEmpty())throw new BizException(1503,"图片素材未补齐");for(String id:m.imageAssetIds){cardImage(approved(s,id,"IMAGE"));uploaded(s,id,"IMAGE");}}
            case "CAROUSEL" -> {if(m.carouselId==null)throw new BizException(1503,"图文尚未组装");carousels.ensure(new TenantId(s.tenantId),m.carouselId,s.accountId);}
            default -> throw new BizException(1503,"不支持的投放素材类型");
        }
    }
    /** A library image can be stored successfully without being eligible for a square product card. */
    public static void cardImage(AssetUploadService.Detail image) {
        if(image.width()==null||image.height()==null||image.width()<=0||image.height()<=0)
            throw new BizException(1520,"商品卡图片缺少可核验的尺寸，请在素材中心补齐文件信息");
        if(!image.width().equals(image.height()))throw new BizException(1520,"商品卡图片须为 1:1 方图，请裁切为方图或选择其他图片");
    }
    public ObjectNode build(CreationState s) {
        settings(s);
        var mode=DeliveryInputPolicy.mode(s.smartBidType);
        var basic=com.lynx.ad.modules.delivery.domain.service.ProductBasicAnalysisService.analyze(s.products);
        if(!basic.canContinue())throw new BizException(1503,"商品基础检查未通过，请处理不可投商品后再发布");
        if(s.confirmedVersion==null||s.confirmedVersion!=s.packageVersion)throw new BizException(1503,"素材包已变更，请重新确认当前版本");
        var tenant=new TenantId(s.tenantId);var c=gateway.credential(tenant,s.accountId,s.awemeId);
        var root=json.createObjectNode();root.put("advertiser_id",CreationQianchuanGateway.numericId(c.advertiserId().value()));
        root.put("aweme_id",CreationQianchuanGateway.numericId(s.awemeId));root.put("name",DeliveryInputPolicy.name(s.name));root.put("marketing_goal","VIDEO_PROM_GOODS");
        var ids=root.putArray("product_ids");s.products.forEach(p->ids.add(CreationQianchuanGateway.numericId(p.qcProductId())));
        var setting=root.putObject("delivery_setting");setting.put("smart_bid_type",mode.oceanValue());setting.put("budget",s.budget);
        setting.put("deep_external_action","AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI");
        if(mode==SmartBidType.CUSTOM)setting.put("roi2_goal",s.roi2Goal);
        else if(recommendationMatches(s))for(var estimate:s.recommendations.path("budget").path("estimate_trans_cost_list")) {
            if(!estimate.path("budget").isNumber()||estimate.path("budget").decimalValue().compareTo(s.budget)!=0)continue;
            copyEstimate(setting,estimate,"min_estimate_convert","min_estimate_convert");copyEstimate(setting,estimate,"estimate_convert","estimate_convert");
            copyEstimate(setting,estimate,"estimate_roi","estimate_roi_goal");copyEstimate(setting,estimate,"min_estimate_roi","min_estimate_roi_goal");break;
        }
        setting.put("video_schedule_type","DATE_RANGE".equals(s.scheduleType)?"SCHEDULE_START_END":"SCHEDULE_FROM_NOW");
        if("DATE_RANGE".equals(s.scheduleType)){setting.put("start_time",s.startDate);setting.put("end_time",s.endDate);}
        root.set("multi_product_creative_list",creatives(s));
        return root;
    }
    /** Build creative bindings independently of creation-only budget and future-date validation. */
    public ArrayNode creatives(CreationState s) {
        var list=json.createArrayNode();
        var tenant=new TenantId(s.tenantId);
        for(var product:s.products) {
            var materials=s.materials.stream().filter(m->product.qcProductId().equals(m.productId)).toList();
            if(materials.isEmpty())throw new BizException(1503,"商品尚无投放素材："+product.name());
            var creative=list.addObject();creative.put("product_id",CreationQianchuanGateway.numericId(product.qcProductId()));creative.put("hide_in_aweme",s.hideInAweme);
            var titles=new LinkedHashSet<String>();String cardImageId=null,cardTitle=null;
            var pool=s.titlePools.get(product.qcProductId());
            if(pool!=null){titles.addAll(pool.customTitles);titles.addAll(pool.cardTitles);if(!pool.cardTitles.isEmpty())cardTitle=pool.cardTitles.getFirst();}
            for(var m:materials) {
                materialReady(s,m);if(pool==null||("IMAGE".equals(m.type)?pool.cardTitles:pool.customTitles).isEmpty())m.titles.stream().map(DeliveryInputPolicy::text).forEach(titles::add);
                if("VIDEO".equals(m.type)) {
                    var v=creative.has("video_material")?(ArrayNode)creative.get("video_material"):creative.putArray("video_material");
                    v.addObject().put("image_mode",m.imageMode==null?"VIDEO_VERTICAL":m.imageMode).put("video_id",uploaded(s,m.videoAssetId,"VIDEO").objectId()).put("video_cover_id",uploaded(s,m.coverAssetId,"IMAGE").objectId());
                }else if("IMAGE".equals(m.type)) {
                    var images=creative.has("image_material")?(ArrayNode)creative.get("image_material"):creative.putArray("image_material");
                    if(m.imageMode!=null&&!"SQUARE".equals(m.imageMode))throw new BizException(1503,"商品卡图片仅支持 SQUARE 方图");
                    for(String id:m.imageAssetIds) {
                        String imageId=uploaded(s,id,"IMAGE").objectId();
                        images.addObject().put("image_mode","SQUARE").putArray("image_ids").add(imageId);
                        if(cardImageId==null){cardImageId=imageId;if(cardTitle==null)cardTitle=DeliveryInputPolicy.text(m.titles.getFirst());}
                    }
                }else {
                    var cards=creative.has("carousel_material")?(ArrayNode)creative.get("carousel_material"):creative.putArray("carousel_material");
                    cards.addObject().put("carousel_id",CreationQianchuanGateway.numericId(carousels.ensure(tenant,m.carouselId,s.accountId)));
                }
            }
            // Product-card images require their own title regardless of the aweme relationship type.
            boolean commodityTitle=creative.has("image_material")||gateway.commodityCardTitle(tenant,s.accountId,s.awemeId);
            if(titles.isEmpty()||titles.size()>30)throw new BizException(1503,"每个商品最多使用 30 条不同的标题（包含商品卡标题）");
            var titleList=creative.putArray("title_material");
            String commodityCopy=commodityTitle?(cardTitle==null?titles.iterator().next():cardTitle):null;
            if(commodityTitle)titleList.addObject().put("title",commodityCopy).put("title_type","COMMODITY_CARD");
            // Qianchuan rejects identical copy even when it is submitted under different title types.
            titles.stream().filter(t->!t.equals(commodityCopy)).forEach(t->titleList.addObject().put("title",t).put("title_type","CUSTOM"));
            // Every bound-aweme product needs a promotion card. Reuse an image already synced to this account.
            if(cardImageId==null) {
                var video=materials.stream().filter(m->"VIDEO".equals(m.type)).findFirst();
                if(video.isPresent())cardImageId=uploaded(s,video.get().coverAssetId,"IMAGE").objectId();
                else {
                    var image=materials.stream().flatMap(m->m.imageAssetIds.stream()).findFirst()
                            .orElseThrow(()->new BizException(1503,"商品缺少已同步千川的投放卡片配图："+product.name()));
                    cardImageId=uploaded(s,image,"IMAGE").objectId();
                }
            }
            var card=creative.putObject("creative_card");card.put("promotion_card_image_id",cardImageId);
            card.put("promotion_card_title","视频同款商品");card.put("promotion_card_action_button","查看详情");
            card.put("promotion_card_button_smart_optimization",false);
            // Prefer an existing complete title; a neutral fallback avoids inventing product claims or truncating copy.
            String sellingPoint=titles.stream().filter(t->platformLength(t)>=11&&platformLength(t)<=18).findFirst().orElse("查看商品详细信息");
            card.putArray("promotion_card_selling_points").add(sellingPoint);
        }
        return list;
    }
    private static boolean recommendationMatches(CreationState s) {
        if(s.recommendations==null)return false;
        // Old snapshots do not prove an account/selection binding; do not send their quote fields.
        if(!s.accountId.equals(s.recommendations.path("accountId").asText())||!s.awemeId.equals(s.recommendations.path("awemeId").asText()))return false;
        var ids=new HashSet<String>();s.recommendations.path("productIds").forEach(id->ids.add(id.asText()));
        return ids.equals(new HashSet<>(s.products.stream().map(CreationState.Product::qcProductId).toList()));
    }
    /** Bind one generated visual only; no changes to product membership or delivery settings. */
    public ObjectNode optimizationMaterial(CreationState s,CreationState.Material m){
        materialReady(s,m);var node=json.createObjectNode();node.put("product_id",CreationQianchuanGateway.numericId(m.productId));
        if("VIDEO".equals(m.type))node.putArray("video_material").addObject().put("image_mode",m.imageMode).put("video_id",uploaded(s,m.videoAssetId,"VIDEO").objectId()).put("video_cover_id",uploaded(s,m.coverAssetId,"IMAGE").objectId());
        else if("IMAGE".equals(m.type)){if(m.imageAssetIds.size()!=1)throw new BizException(1503,"自动商品卡只允许一张图片");node.putArray("image_material").addObject().put("image_mode","SQUARE").putArray("image_ids").add(uploaded(s,m.imageAssetIds.getFirst(),"IMAGE").objectId());}
        else if("CAROUSEL".equals(m.type))node.putArray("carousel_material").addObject().put("carousel_id",CreationQianchuanGateway.numericId(carousels.ensure(new TenantId(s.tenantId),m.carouselId,s.accountId)));
        else throw new BizException(1503,"不支持的自动生成形式");
        node.putArray("title_material").addObject().put("title",m.titles.getFirst()).put("title_type","IMAGE".equals(m.type)?"COMMODITY_CARD":"CUSTOM");return node;
    }
    private static int platformLength(String text) {
        return DeliveryInputPolicy.platformLength(text);
    }
    private static void copyEstimate(ObjectNode target,JsonNode row,String from,String to) {
        if(row.path(from).isNumber()&&row.path(from).decimalValue().signum()>=0)target.set(to,row.get(from));
    }
}
