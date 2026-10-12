package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.Sync;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
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
        if(s.smartBidType==null||!Set.of("CUSTOM","CONSERVATIVE").contains(s.smartBidType))throw new BizException(1402,"请选择控成本或放量投放");
        if(s.budget==null || s.budget.signum()<=0 || s.budget.compareTo(new BigDecimal("100000"))>0 || s.budget.stripTrailingZeros().scale()>2)throw new BizException(1402,"日预算须大于 0、不超过 100000 元且最多两位小数");
        if("CUSTOM".equals(s.smartBidType)&&(s.roi2Goal==null||s.roi2Goal.compareTo(new BigDecimal("0.1"))<0||s.roi2Goal.compareTo(new BigDecimal("50"))>0||s.roi2Goal.stripTrailingZeros().scale()>2))throw new BizException(1402,"净成交 ROI 目标须在 0.1–50 之间且最多两位小数");
        if(s.scheduleType==null||!Set.of("FROM_NOW","DATE_RANGE").contains(s.scheduleType))throw new BizException(1402,"投放日期类型不合法");
        if("DATE_RANGE".equals(s.scheduleType))try{
            var start=LocalDate.parse(s.startDate);var end=LocalDate.parse(s.endDate);var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
            if(start.isBefore(today)||end.isBefore(start))throw new IllegalArgumentException();
        }catch(Exception e){throw new BizException(1402,"开始日期不得早于今天，结束日期不得早于开始日期");}
        if(s.name==null||s.name.isBlank()||s.name.length()>30)throw new BizException(1402,"计划名称须为 1–30 字");
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
        approved(s,m.textAssetId,"TEXT");
        switch(m.type) {
            case "VIDEO" -> {uploaded(s,m.videoAssetId,"VIDEO");uploaded(s,m.coverAssetId,"IMAGE");}
            case "IMAGE" -> {if(m.imageAssetIds.isEmpty())throw new BizException(1503,"图片素材未补齐");for(String id:m.imageAssetIds)uploaded(s,id,"IMAGE");}
            case "CAROUSEL" -> {if(m.carouselId==null)throw new BizException(1503,"图文尚未组装");carousels.ensure(new TenantId(s.tenantId),m.carouselId,s.accountId);}
            default -> throw new BizException(1503,"不支持的投放素材类型");
        }
        if(m.titles.isEmpty()||m.titles.stream().anyMatch(x->x==null||x.isBlank()||x.length()>55||platformLength(x)<10||platformLength(x)>110))
            throw new BizException(1503,"素材标题须满足千川的 10–110 字符要求（汉字按 2 个字符计算，最多 55 字）");
    }
    public ObjectNode build(CreationState s) {
        settings(s);
        if(!"SUCCEEDED".equals(s.analysisStatus))throw new BizException(1503,"请先完成本次选品分析");
        if(s.confirmedVersion==null||s.confirmedVersion!=s.packageVersion)throw new BizException(1503,"素材包已变更，请重新确认当前版本");
        var tenant=new TenantId(s.tenantId);var c=gateway.credential(tenant,s.accountId,s.awemeId);
        var root=json.createObjectNode();root.put("advertiser_id",CreationQianchuanGateway.numericId(c.advertiserId().value()));
        root.put("aweme_id",CreationQianchuanGateway.numericId(s.awemeId));root.put("name",s.name);root.put("marketing_goal","VIDEO_PROM_GOODS");
        var ids=root.putArray("product_ids");s.products.forEach(p->ids.add(CreationQianchuanGateway.numericId(p.qcProductId())));
        var setting=root.putObject("delivery_setting");setting.put("smart_bid_type",com.lynx.ad.modules.delivery.domain.model.SmartBidType.valueOf(s.smartBidType).oceanValue());setting.put("budget",s.budget);
        setting.put("deep_external_action","AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI");
        if("CUSTOM".equals(s.smartBidType))setting.put("roi2_goal",s.roi2Goal);
        else if(s.recommendations!=null)for(var estimate:s.recommendations.path("budget").path("estimate_trans_cost_list")) {
            if(!estimate.path("budget").isNumber()||estimate.path("budget").decimalValue().compareTo(s.budget)!=0)continue;
            copyEstimate(setting,estimate,"min_estimate_convert","min_estimate_convert");copyEstimate(setting,estimate,"estimate_convert","estimate_convert");
            copyEstimate(setting,estimate,"estimate_roi","estimate_roi_goal");copyEstimate(setting,estimate,"min_estimate_roi","min_estimate_roi_goal");break;
        }
        setting.put("video_schedule_type","DATE_RANGE".equals(s.scheduleType)?"SCHEDULE_START_END":"SCHEDULE_FROM_NOW");
        if("DATE_RANGE".equals(s.scheduleType)){setting.put("start_time",s.startDate);setting.put("end_time",s.endDate);}
        var list=root.putArray("multi_product_creative_list");
        for(var product:s.products) {
            var materials=s.materials.stream().filter(m->product.qcProductId().equals(m.productId)).toList();
            if(materials.isEmpty())throw new BizException(1503,"商品尚无投放素材："+product.name());
            var creative=list.addObject();creative.put("product_id",CreationQianchuanGateway.numericId(product.qcProductId()));creative.put("hide_in_aweme",s.hideInAweme);
            var titles=new LinkedHashSet<String>();String cardImageId=null,cardTitle=null;
            for(var m:materials) {
                materialReady(s,m);m.titles.stream().map(String::strip).forEach(titles::add);
                if("VIDEO".equals(m.type)) {
                    var v=creative.has("video_material")?(ArrayNode)creative.get("video_material"):creative.putArray("video_material");
                    v.addObject().put("image_mode",m.imageMode==null?"VIDEO_VERTICAL":m.imageMode).put("video_id",uploaded(s,m.videoAssetId,"VIDEO").objectId()).put("video_cover_id",uploaded(s,m.coverAssetId,"IMAGE").objectId());
                }else if("IMAGE".equals(m.type)) {
                    var images=creative.has("image_material")?(ArrayNode)creative.get("image_material"):creative.putArray("image_material");
                    if(m.imageMode!=null&&!"SQUARE".equals(m.imageMode))throw new BizException(1503,"商品卡图片仅支持 SQUARE 方图");
                    for(String id:m.imageAssetIds) {
                        String imageId=uploaded(s,id,"IMAGE").objectId();
                        images.addObject().put("image_mode","SQUARE").putArray("image_ids").add(imageId);
                        if(cardImageId==null){cardImageId=imageId;cardTitle=m.titles.getFirst().strip();}
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
        return root;
    }
    private static int platformLength(String text) {
        return text.codePoints().map(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN?2:1).sum();
    }
    private static void copyEstimate(ObjectNode target,JsonNode row,String from,String to) {
        if(row.path(from).isNumber()&&row.path(from).decimalValue().signum()>=0)target.set(to,row.get(from));
    }
}
