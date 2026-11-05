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
            List<String> reasons,Map<String,BigDecimal> metrics,List<String> titles) {}
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
    private com.lynx.ad.modules.delivery.infrastructure.persistence.PlanMaterialPresentationStore labels;
    @org.springframework.beans.factory.annotation.Autowired public void presentationStore(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanMaterialPresentationStore value) {labels=value;}
    public PlanMaterialsService(AdPlanRepository plans,OauthAccountRepository accounts,QianchuanCredentialFacade credentials,AdManagementPort port,AssetUploadService assets) {
        this.plans=plans;this.accounts=accounts;this.credentials=credentials;this.port=port;this.assets=assets;
    }
    public Result materials(TenantId tenant,PlanId id,PlanStatisticsRange range,String type) {
        return materials(tenant,id,range,type,false);
    }
    public Result materials(TenantId tenant,PlanId id,PlanStatisticsRange range,String type,boolean currentOnly) {
        if(!TYPES.contains(type))throw new BizException(1402,"素材分类不正确");
        var plan=plans.findById(tenant,id).filter(p->p.status()!=PlanStatus.DELETED).orElseThrow(()->new BizException(1405,"计划不存在"));
        if(plan.qcPlanId().isEmpty())throw new BizException(1403,"草稿尚未发布，暂无千川素材统计");
        var account=accounts.findById(tenant,plan.accountId()).orElseThrow(()->new BizException(1201,"授权账户不存在"));
        if(account.platform()!=Platform.QIANCHUAN || !"ACTIVE".equals(account.status()))throw new BizException(1201,"广告账户授权已失效，请重新授权");
        var credential=credentials.getUsableCredential(tenant,plan.accountId());String qcId=plan.qcPlanId().orElseThrow().value();
        JsonNode current=MissingNode.getInstance();
        if(currentOnly) {
            current=port.detail(credential,qcId);
            if(current==null||!qcId.equals(QianchuanPlanMapping.id(current)))throw new BizException(1406,"素材配置的计划ID不匹配");
            com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanCreativeBindings.groups(current);
        }
        List<Material> result=new ArrayList<>();List<String> warnings=new ArrayList<>();
        if(currentOnly&&"TITLE".equals(type)) {
            for(var group:current.path("multi_product_creative_list"))for(var title:group.path("title_material")) {
                String product=group.path("product_id").asText(),text=title.path("title").asText();
                result.add(new Material(product+":"+result.size(),"TITLE",text,text,null,null,List.of(),null,List.of(new Product(product,"商品 "+product,null)),null,null,false,null,List.of(),null,List.of(),Map.of(),List.of(text)));
            }
            return new Result(List.copyOf(result),Instant.now(),null);
        }
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
            if(!currentOnly&&"VIDEO".equals(nativeType))try {for(var row:port.materialAttributes(credential,ids))attributes.put(row.path("material_id").asText(),row);}
            catch(BizException ex){warnings.add("素材标签暂不可用："+ex.getMessage());}
            JsonNode detail=current;
            if(!currentOnly && !rows.isEmpty() && !"TITLE".equals(nativeType) && !"LIVE_ROOM".equals(nativeType))try {
                var response=port.detail(credential,qcId);
                if(response!=null && qcId.equals(QianchuanPlanMapping.id(response)))detail=response;
                else if(response!=null)warnings.add("素材投放配置的计划ID不匹配，已保留素材库预览信息");
            }catch(BizException ex){warnings.add("素材投放配置暂不可用："+ex.getMessage());}
            Map<String,Presentation> presentations=new HashMap<>();
            for(var row:rows) {
                var sub=subtype(row,nativeType);String mid=sub.path("material_id").asText();
                presentations.put(mid,presentation(detail,row,sub,metadata.getOrDefault(mid,MissingNode.getInstance()),nativeType));
            }
            Map<String,String> covers=new HashMap<>();
            var coverIds=presentations.values().stream().map(Presentation::coverId).filter(Objects::nonNull).distinct().toList();
            if(!coverIds.isEmpty())try {
                for(var image:port.materialImageMetadata(credential,coverIds)) {
                    String coverId=text(image,"id"),url=text(image,"url");
                    if(coverId!=null && coverIds.contains(coverId) && url!=null)covers.put(coverId,url);
                }
            }catch(BizException ex){warnings.add("投放封面暂不可用："+ex.getMessage());}
            var seen=new HashSet<String>();
            for(var row:rows) {
                if(row.hasNonNull("ad_id") && !qcId.equals(row.path("ad_id").asText()))throw new BizException(1406,"素材报表的计划ID不匹配");
                var info=row.path("material_info");
                if(info.hasNonNull("material_type") && !nativeType.equals(info.path("material_type").asText()))throw new BizException(1406,"千川素材分类与查询分类不一致");
                var sub=subtype(row,nativeType);String materialId=sub.path("material_id").asText();
                if(!seen.add(materialId))throw new BizException(1406,"素材报表含重复素材，请重新查询");
                JsonNode meta=metadata.getOrDefault(materialId,MissingNode.getInstance()),attr=attributes.getOrDefault(materialId,MissingNode.getInstance());
                if(currentOnly&&!currentlyBound(detail,row,sub,meta,nativeType))continue;
                var presentation=presentations.get(materialId);
                Boolean aigc=flag(row,info,sub,meta);
                if("AIGC".equals(type) && !Boolean.TRUE.equals(aigc))continue;
                Map<String,BigDecimal> metrics=new LinkedHashMap<>();FIELDS.forEach((k,v)->metrics.put(k,QianchuanPlanMapping.number(row.path("stats_info"),v)));
                var productInfo=new LinkedHashMap<String,Product>();
                for(var p:row.path("product_info")) {String product=p.path("product_id").asText();if(!product.isBlank())productInfo.put(product,new Product(product,p.path("product_name").asText("商品 "+product),text(p,"product_image")));}
                for(var p:row.path("product_id_list"))productInfo.putIfAbsent(p.asText(),new Product(p.asText(),"商品 "+p.asText(),null));
                var images=new ArrayList<String>();
                for(var image:meta.path("images")){String imageUrl=text(image,"url");if(imageUrl!=null)images.add(imageUrl);}
                if(images.isEmpty())for(var image:sub.path("image")){String imageUrl=text(image,"image_url");if(imageUrl!=null)images.add(imageUrl);}
                String poster=first(covers.get(presentation.coverId()),text(sub.path("cover_image"),"image_url"),text(sub.path("cover_image"),"url"),text(meta,"poster_url"),text(sub,"image_url"));
                if("IMAGE".equals(nativeType)) {String image=first(text(sub,"image_url"),text(meta,"url"));if(image!=null)images.add(image);poster=first(poster,image);}
                if(poster==null && !images.isEmpty())poster=images.getFirst();
                var titles=presentation.titles();String originalName=null;
                if(labels!=null && !titles.isEmpty()) {
                    String objectId=first(text(sub,"video_id"),text(sub,"image_id"),text(meta,"id"));
                    try {
                        var label=labels.label(tenant,plan.accountId(),qcId,nativeType,objectId,productInfo.keySet(),titles);
                        if(label.isPresent()){originalName=label.get().name();titles=label.get().titles();}
                    }catch(org.springframework.dao.DataAccessException unavailable){warnings.add("原始素材名称暂不可用，已展示千川投放标题");}
                }
                String name=first(originalName,readableTitle(text(sub,"title")),titles.isEmpty()?null:titles.getFirst(),readableTitle(text(meta,"filename")),
                        productInfo.values().stream().map(Product::name).findFirst().orElse(null),"素材 "+materialId);
                var tags=new LinkedHashSet<String>();
                for(var source:List.of(row,info,sub,meta)) {for(var tag:source.path("analysis_type"))tags.add(tag.asText());for(var tag:source.path("tags"))tags.add(tag.asText());for(var tag:source.path("tag"))tags.add(tag.asText());}
                for(var entry:Map.of("is_first_publish_material","FIRST_PUBLISH_MATERIAL","is_ecp_high_quality_material","HIGH_QUALITY_MATERIAL",
                    "is_ecp_low_quality_material","LOW_QUALITY_MATERIAL","is_carry_material","CARRY_MATERIAL","is_inefficient_material","INEFFICIENT_MATERIAL","is_similar_material","SIMILAR_MATERIAL").entrySet())if(attr.path(entry.getKey()).asBoolean(false))tags.add(entry.getValue());
                // Report music_url can retain an expired VOD address; the library renews its audio URL.
                String videoUrl=first(text(meta,"url"),text(sub,"url")),musicUrl=first(text(meta.path("audio"),"url"),text(sub,"music_url"));
                // Qianchuan CDN media can reject browser referrers. Exact uploaded object IDs preserve the original file version.
                if("VIDEO".equals(nativeType))videoUrl=first(registeredUrl(tenant,plan.accountId(),"VIDEO",first(text(sub,"video_id"),text(meta,"id"))),videoUrl);
                if("CAROUSEL".equals(nativeType))musicUrl=first(registeredUrl(tenant,plan.accountId(),"AUDIO",text(meta.path("audio"),"video_id")),musicUrl);
                result.add(new Material(materialId,nativeType,name,first(text(sub,"description"),text(meta,"description"),readableTitle(text(sub,"title"))),videoUrl,poster,List.copyOf(images),musicUrl,
                    List.copyOf(productInfo.values()),text(row,"audit_status"),text(row,"material_status"),row.path("is_delete").asBoolean(false),aigc,List.copyOf(tags),
                    first(text(row,"create_time"),text(info,"create_time"),text(sub,"create_time"),text(meta,"create_time")),strings(row.path("delivery_not_reason")),Collections.unmodifiableMap(metrics),titles));
            }
        }
        if("AIGC".equals(type))warnings.add("AIGC 分类依据千川素材库的 AI 标识；未返回标识的素材仍在视频、图片或图文分类中展示。");
        return new Result(List.copyOf(result),Instant.now(),warnings.isEmpty()?null:String.join("；",new LinkedHashSet<>(warnings)));
    }
    /** Reports can retain removed historical media; the editing preview shows current bindings only. */
    private static boolean currentlyBound(JsonNode detail,JsonNode row,JsonNode sub,JsonNode meta,String type) {
        if(row.path("is_delete").asBoolean())return false;
        var products=new HashSet<String>();for(var product:row.path("product_id_list"))products.add(product.asText());
        for(var product:row.path("product_info"))if(product.hasNonNull("product_id"))products.add(product.path("product_id").asText());
        String id=first(text(sub,"video_id"),text(sub,"image_id"),text(sub,"carousel_id"),text(meta,"id"));
        String nativeId=first(text(sub,"aweme_item_id"),text(sub,"aweme_carousel_id"));
        for(var group:detail.path("multi_product_creative_list")) {
            if(!products.isEmpty()&&!products.contains(group.path("product_id").asText()))continue;
            if("VIDEO".equals(type))for(String field:List.of("video_material","block_video_material"))for(var visual:group.path(field))
                if(id!=null&&id.equals(text(visual,"video_id"))||nativeId!=null&&!"0".equals(nativeId)&&nativeId.equals(text(visual,"aweme_item_id")))return true;
            if("IMAGE".equals(type))for(var visual:group.path("image_material"))for(var image:visual.path("image_ids"))if(id!=null&&id.equals(image.asText()))return true;
            if("CAROUSEL".equals(type))for(var visual:group.path("carousel_material"))
                if(id!=null&&id.equals(text(visual,"carousel_id"))||nativeId!=null&&!"0".equals(nativeId)&&nativeId.equals(text(visual,"aweme_carousel_id")))return true;
        }
        return false;
    }
    private static JsonNode subtype(JsonNode row,String type) {
        return row.path("material_info").path(switch(type){case "CAROUSEL"->"carousel_material";case "TITLE"->"title_material";case "IMAGE"->"image_material";case "LIVE_ROOM"->"room_material";default->"video_material";});
    }
    private record Presentation(List<String> titles,String coverId) {}
    private static Presentation presentation(JsonNode detail,JsonNode row,JsonNode sub,JsonNode meta,String type) {
        var titles=new LinkedHashSet<String>();String ownTitle=readableTitle(text(sub,"title"));
        if(ownTitle!=null)titles.add(ownTitle);
        String objectId=first(text(sub,"video_id"),text(sub,"image_id"),text(meta,"id")),cover=null;
        var products=new HashSet<String>();
        for(var product:row.path("product_id_list"))products.add(product.asText());
        for(var product:row.path("product_info"))if(product.hasNonNull("product_id"))products.add(product.path("product_id").asText());
        var matching=new ArrayList<JsonNode>();
        for(var group:detail.path("multi_product_creative_list")) {
            if(!products.contains(group.path("product_id").asText()))continue;
            boolean bound=false;
            if("VIDEO".equals(type) && objectId!=null)for(var visual:group.path("video_material"))if(objectId.equals(text(visual,"video_id"))) {
                bound=true;String candidate=text(visual,"video_cover_id");
                if(cover!=null && candidate!=null && !cover.equals(candidate))return new Presentation(List.copyOf(titles),null);
                if(candidate!=null)cover=candidate;
            }
            if("IMAGE".equals(type) && objectId!=null)for(var visual:group.path("image_material"))for(var image:visual.path("image_ids"))if(objectId.equals(image.asText()))bound=true;
            if("CAROUSEL".equals(type))for(var visual:group.path("carousel_material"))if(sub.path("material_id").asText().equals(visual.path("carousel_id").asText()))bound=true;
            if(bound)matching.add(group);
        }
        if(ownTitle==null)for(var group:matching)for(var title:group.path("title_material")) {
            String titleType=text(title,"title_type");
            if(titleType!=null && !("IMAGE".equals(type)?"COMMODITY_CARD":"CUSTOM").equals(titleType))continue;
            String value=readableTitle(text(title,"title"));if(value!=null)titles.add(value);
        }
        return new Presentation(List.copyOf(titles),cover);
    }
    private static String readableTitle(String value) {
        if(value==null || value.matches("(?i).+\\.(mp4|mov|webm|m4v|avi|jpe?g|png|webp|gif|mp3|wav|m4a|aac)"))return null;
        return value;
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
