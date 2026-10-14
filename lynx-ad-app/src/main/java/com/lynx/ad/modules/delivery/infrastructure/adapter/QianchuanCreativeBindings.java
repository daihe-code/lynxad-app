package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.exception.BizException;
import java.util.*;

/** Editable creative fields from authoritative platform detail; audit snapshots remain immutable. */
public final class QianchuanCreativeBindings {
    private QianchuanCreativeBindings(){}
    public static ArrayNode groups(JsonNode detail){
        if(!detail.path("multi_product_creative_list").isArray() || detail.path("multi_product_creative_list").isEmpty())throw new BizException(1803,"千川未返回完整多商品素材绑定，暂停内容操作");
        var result=new ObjectMapper().createArrayNode();var ids=new HashSet<String>();
        for(var g:detail.path("multi_product_creative_list")){String id=g.path("product_id").asText();LiveAdManagementAdapter.numericId(id);if(!ids.add(id))throw new BizException(1803,"商品绑定重复");var copy=select(g,"product_id","hide_in_aweme","creative_type","creative_card");
            copy.put("product_id",LiveAdManagementAdapter.numericId(id));
            for(String field:List.of("video_material","image_material","carousel_material","title_material","block_video_material")){
                if(!g.has(field))continue;if(!g.path(field).isArray())throw new BizException(1803,"素材绑定格式不完整");var list=copy.putArray(field);
                for(var item:g.path(field)){var clean=switch(field){case "video_material","block_video_material"->select(item,"video_id","video_cover_id","aweme_item_id","image_mode");case "image_material"->select(item,"image_ids","image_mode");case "carousel_material"->select(item,"carousel_id","aweme_carousel_id");default->select(item,"title","title_type","dynamic_words");};
                    for(String number:List.of("aweme_item_id","carousel_id","aweme_carousel_id"))if(clean.hasNonNull(number))clean.put(number,LiveAdManagementAdapter.numericId(clean.path(number).asText()));
                    list.add(clean);
                }
            }result.add(copy);
        }var sorted=new ArrayList<JsonNode>();result.forEach(sorted::add);sorted.sort(Comparator.comparing(n->n.path("product_id").asText()));result.removeAll();sorted.forEach(result::add);return result;
    }
    private static ObjectNode select(JsonNode n,String... keys){var result=new ObjectMapper().createObjectNode();for(String key:keys)if(n.hasNonNull(key))result.set(key,n.get(key).deepCopy());return result;}
}
