package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PlanMaterialsService {
    public record Product(String id,String name,String imageUrl) {}
    public record Material(String id,String type,String name,String text,String previewUrl,String posterUrl,List<String> imageUrls,String musicUrl,
            List<Product> products,String auditStatus,String status,boolean deleted,Boolean aigc,List<String> tags,String createTime,
            List<String> reasons,Map<String,BigDecimal> metrics) {}
    public record Result(List<Material> records,Instant fetchedAt,String warning) {}
    private static final Set<String> TYPES=Set.of("VIDEO","AIGC","IMAGE","CAROUSEL","OTHER","TITLE");
    private static final Map<String,String> FIELDS=new LinkedHashMap<>();
    static {
        FIELDS.put("shows","product_show_count_for_roi2");FIELDS.put("clicks","product_click_count_for_roi2");
        FIELDS.put("ctr","product_cvr_rate_for_roi2");FIELDS.put("cvr","product_convert_rate_for_roi2");
        FIELDS.put("cost","stat_cost_for_roi2");FIELDS.put("orders","total_pay_order_count_for_roi2");
        FIELDS.put("gmv","total_pay_order_gmv_include_coupon_for_roi2");FIELDS.put("roi","total_prepay_and_pay_order_roi2");
        FIELDS.put("orderCost","total_cost_per_pay_order_for_roi2");FIELDS.put("netRoi","total_prepay_and_pay_settle_roi2_1h");
        FIELDS.put("netGmv","total_order_settle_amount_for_roi2_1h");FIELDS.put("settlementRate","total_order_settle_amount_rate_for_roi2_1h");
        FIELDS.put("refundRate","total_refund_order_gmv_for_roi2_1h_rate");
    }
    private final AdPlanRepository plans;
    private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;
    private final AdManagementPort port;
    private final AssetUploadService assets;
    public PlanMaterialsService(AdPlanRepository plans,OauthAccountRepository accounts,QianchuanCredentialFacade credentials,AdManagementPort port,AssetUploadService assets) {
        this.plans=plans;this.accounts=accounts;this.credentials=credentials;this.port=port;this.assets=assets;
    }
    public Result materials(TenantId tenant,PlanId id,PlanStatisticsRange range,String type) {
        if(!TYPES.contains(type))throw new BizException(1402,"素材分类不正确");
        var plan=plans.findById(tenant,id).filter(p->p.status()!=PlanStatus.DELETED).orElseThrow(()->new BizException(1405,"计划不存在"));
        if(plan.qcPlanId().isEmpty())throw new BizException(1403,"草稿尚未发布，暂无千川素材统计");
        var account=accounts.findById(tenant,plan.accountId()).orElseThrow(()->new BizException(1201,"授权账户不存在"));
        if(account.platform()!=Platform.QIANCHUAN || !"ACTIVE".equals(account.status()))throw new BizException(1201,"广告账户授权已失效，请重新授权");
        var credential=credentials.getUsableCredential(tenant,plan.accountId());String qcId=plan.qcPlanId().orElseThrow().value();
        List<Material> result=new ArrayList<>();List<String> warnings=new ArrayList<>();
        var nativeTypes="AIGC".equals(type)?List.of("VIDEO","IMAGE","CAROUSEL"):List.of("OTHER".equals(type)?"LIVE_ROOM":type);
        for(String nativeType:nativeTypes) {
            List<JsonNode> rows;
            try {rows=port.materialReport(credential,qcId,range.startDate(),range.endDate(),nativeType);}
            catch(BizException ex) {
                if("OTHER".equals(type) && ex.getMessage().contains("商品全域投放不支持直播间画面"))return new Result(List.of(),Instant.now(),"当前计划为商品全域投放，千川不支持查询直播间画面素材。");
                throw ex;
            }
            List<String> ids=rows.stream().map(r->subtype(r,nativeType).path("material_id").asText()).distinct().toList();
            for(String materialId:ids)if(!materialId.matches("[1-9][0-9]*"))throw new BizException(1406,"千川返回了不完整的素材ID");
            Map<String,JsonNode> metadata=new HashMap<>(),attributes=new HashMap<>();
            try {for(var row:port.materialMetadata(credential,nativeType,ids))metadata.put(row.path("material_id").asText(),row);}
            catch(BizException ex){warnings.add("素材库信息暂不可用："+ex.getMessage());}
            if("VIDEO".equals(nativeType))try {for(var row:port.materialAttributes(credential,ids))attributes.put(row.path("material_id").asText(),row);}
            catch(BizException ex){warnings.add("素材标签暂不可用："+ex.getMessage());}
            var seen=new HashSet<String>();
            for(var row:rows) {
                if(row.hasNonNull("ad_id") && !qcId.equals(row.path("ad_id").asText()))throw new BizException(1406,"素材报表的计划ID不匹配");
                var info=row.path("material_info");
                if(info.hasNonNull("material_type") && !nativeType.equals(info.path("material_type").asText()))throw new BizException(1406,"千川素材分类与查询分类不一致");
                var sub=subtype(row,nativeType);String materialId=sub.path("material_id").asText();
                if(!seen.add(materialId))throw new BizException(1406,"素材报表含重复素材，请重新查询");
                JsonNode meta=metadata.getOrDefault(materialId,MissingNode.getInstance()),attr=attributes.getOrDefault(materialId,MissingNode.getInstance());
                Boolean aigc=flag(row,info,sub,meta);
                if("AIGC".equals(type) && !Boolean.TRUE.equals(aigc))continue;
                Map<String,BigDecimal> metrics=new LinkedHashMap<>();FIELDS.forEach((k,v)->metrics.put(k,QianchuanPlanMapping.number(row.path("stats_info"),v)));
                var productInfo=new LinkedHashMap<String,Product>();
                for(var p:row.path("product_info")) {String product=p.path("product_id").asText();if(!product.isBlank())productInfo.put(product,new Product(product,p.path("product_name").asText("商品 "+product),text(p,"product_image")));}
                for(var p:row.path("product_id_list"))productInfo.putIfAbsent(p.asText(),new Product(p.asText(),"商品 "+p.asText(),null));
                var images=new ArrayList<String>();
                for(var image:meta.path("images")){String imageUrl=text(image,"url");if(imageUrl!=null)images.add(imageUrl);}
                if(images.isEmpty())for(var image:sub.path("image")){String imageUrl=text(image,"image_url");if(imageUrl!=null)images.add(imageUrl);}
                String poster=first(text(sub.path("cover_image"),"image_url"),text(sub.path("cover_image"),"url"),text(meta,"poster_url"),text(sub,"image_url"));
                if("IMAGE".equals(nativeType)) {String image=first(text(sub,"image_url"),text(meta,"url"));if(image!=null)images.add(image);poster=first(poster,image);}
                if(poster==null && !images.isEmpty())poster=images.getFirst();
                String name=first(text(sub,"title"),text(meta,"filename"),text(sub,"aweme_name"),"素材 "+materialId);
                var tags=new LinkedHashSet<String>();
                for(var source:List.of(row,info,sub,meta)) {for(var tag:source.path("analysis_type"))tags.add(tag.asText());for(var tag:source.path("tags"))tags.add(tag.asText());for(var tag:source.path("tag"))tags.add(tag.asText());}
                for(var entry:Map.of("is_first_publish_material","FIRST_PUBLISH_MATERIAL","is_ecp_high_quality_material","HIGH_QUALITY_MATERIAL",
                    "is_ecp_low_quality_material","LOW_QUALITY_MATERIAL","is_carry_material","CARRY_MATERIAL","is_inefficient_material","INEFFICIENT_MATERIAL","is_similar_material","SIMILAR_MATERIAL").entrySet())if(attr.path(entry.getKey()).asBoolean(false))tags.add(entry.getValue());
                String videoUrl=first(text(sub,"url"),text(meta,"url")),musicUrl=first(text(sub,"music_url"),text(meta.path("audio"),"url"));
                // Qianchuan CDN media can reject browser referrers. Exact uploaded object IDs preserve the original file version.
                if("VIDEO".equals(nativeType))videoUrl=first(registeredUrl(tenant,plan.accountId(),"VIDEO",first(text(sub,"video_id"),text(meta,"id"))),videoUrl);
                if("CAROUSEL".equals(nativeType))musicUrl=first(registeredUrl(tenant,plan.accountId(),"AUDIO",text(meta.path("audio"),"video_id")),musicUrl);
                result.add(new Material(materialId,nativeType,name,first(text(sub,"description"),text(meta,"description"),text(sub,"title")),videoUrl,poster,List.copyOf(images),musicUrl,
                    List.copyOf(productInfo.values()),text(row,"audit_status"),text(row,"material_status"),row.path("is_delete").asBoolean(false),aigc,List.copyOf(tags),
                    first(text(row,"create_time"),text(info,"create_time"),text(sub,"create_time"),text(meta,"create_time")),strings(row.path("delivery_not_reason")),Collections.unmodifiableMap(metrics)));
            }
        }
        if("AIGC".equals(type))warnings.add("AIGC 分类依据千川素材库的 AI 标识；未返回标识的素材仍在视频、图片或图文分类中展示。");
        return new Result(List.copyOf(result),Instant.now(),warnings.isEmpty()?null:String.join("；",new LinkedHashSet<>(warnings)));
    }
    private static JsonNode subtype(JsonNode row,String type) {
        return row.path("material_info").path(switch(type){case "CAROUSEL"->"carousel_material";case "TITLE"->"title_material";case "IMAGE"->"image_material";case "LIVE_ROOM"->"room_material";default->"video_material";});
    }
    private static String text(JsonNode node,String key) {String value=node.path(key).asText(null);return value==null || value.isBlank()?null:value;}
    private static String first(String... values) {return Arrays.stream(values).filter(Objects::nonNull).findFirst().orElse(null);}
    private static List<String> strings(JsonNode values) {List<String> result=new ArrayList<>();if(values.isArray())values.forEach(v->result.add(v.asText()));return List.copyOf(result);}
    private static Boolean flag(JsonNode... nodes) {for(var node:nodes)if(node.path("is_ai_create").isBoolean())return node.path("is_ai_create").booleanValue();return null;}
    private String registeredUrl(TenantId tenant,AccountId account,String kind,String objectId) {
        if(objectId==null)return null;
        try {return assets.platformPreviewUrl(tenant,account.value(),kind,objectId).orElse(null);}catch(BizException ex){return null;}
    }
}
