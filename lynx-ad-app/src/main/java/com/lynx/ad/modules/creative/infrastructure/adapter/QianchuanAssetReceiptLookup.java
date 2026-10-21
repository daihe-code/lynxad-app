package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.AssetReceiptLookupPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class QianchuanAssetReceiptLookup implements AssetReceiptLookupPort {
    private final QianchuanRestClient client;private final OceanJson json;private final ObjectMapper mapper;private final LynxAdProperties properties;
    public QianchuanAssetReceiptLookup(QianchuanRestClient client,OceanJson json,ObjectMapper mapper,LynxAdProperties properties){this.client=client;this.json=json;this.mapper=mapper;this.properties=properties;}
    @Override public Result find(Credential c,Expected expected) {
        if(properties.getMock().isQianchuan())return Result.pending("UNAVAILABLE");
        if(expected.kind()==AssetKind.TEXT || expected.signature()==null || !expected.signature().matches("(?i)[0-9a-f]{32}"))return Result.pending("MISSING_EVIDENCE");
        String base=switch(expected.kind()){case IMAGE->properties.getQianchuan().getImageGetUrl();case VIDEO->properties.getQianchuan().getVideoGetUrl();case AUDIO->properties.getQianchuan().getMaterial().getAudioGetUrl();default->throw new IllegalArgumentException();};
        var filtering=mapper.createObjectNode();filtering.putArray("signatures").add(expected.signature().toLowerCase(Locale.ROOT));
        String query="?advertiser_id="+URLEncoder.encode(c.advertiserId().value(),StandardCharsets.UTF_8)+"&page=1&page_size=100&filtering="+URLEncoder.encode(filtering.toString(),StandardCharsets.UTF_8);
        var data=json.dataOrThrow(client.exchange(RequestSpec.get(base+query).header("Access-Token",c.accessToken()).build(),String.class),1506,"上传结果暂无法查询");
        var list=data.path("list");if(!list.isArray())return Result.pending("INCOMPLETE");
        // No account-wide scan or unbounded pagination. An incomplete result cannot prove uniqueness.
        var pages=data.path("page_info");
        if(!pages.path("total_page").isIntegralNumber() && !pages.path("total_number").isIntegralNumber())return Result.pending("INCOMPLETE");
        if(pages.path("page").asInt(1)!=1 || pages.path("total_page").asInt(0)<0 || pages.path("total_number").asLong(0)<0 || pages.path("total_page").asInt(0)>1 || pages.path("total_number").asLong(0)>list.size() || list.size()>=100)return Result.pending("INCOMPLETE");
        Map<String,Result> matches=new LinkedHashMap<>();boolean insufficient=false;
        for(var row:list) {
            if(!expected.signature().equalsIgnoreCase(row.path("signature").asText()))continue;
            if(row.hasNonNull("advertiser_id")&&!c.advertiserId().value().equals(row.path("advertiser_id").asText()))continue;
            if(row.hasNonNull("size")&&row.path("size").asLong(-1)!=expected.sizeBytes())continue;
            if(expected.width()!=null&&row.hasNonNull("width")&&row.path("width").asInt(-1)!=expected.width())continue;
            if(expected.height()!=null&&row.hasNonNull("height")&&row.path("height").asInt(-1)!=expected.height())continue;
            if(expected.kind()!=AssetKind.AUDIO) {
                if(row.hasNonNull("is_ai_create") && (!row.path("is_ai_create").isBoolean() || row.path("is_ai_create").asBoolean()!=expected.aigc()))continue;
                if(expected.aigc()&&!row.hasNonNull("is_ai_create")){insufficient=true;continue;}
            }
            String object=row.path(expected.kind()==AssetKind.AUDIO?"audio_id":"id").asText(),material=row.path("material_id").asText();
            if(expected.objectId()!=null&&!expected.objectId().isBlank()&&!expected.objectId().equals(object))continue;
            if(expected.materialId()!=null&&!expected.materialId().isBlank()&&!expected.materialId().equals(material))continue;
            if(object.isBlank()||object.length()>128||object.contains("://")||!material.matches("[1-9][0-9]{0,30}")){insufficient=true;continue;}
            matches.put(object+":"+material,new Result("CONFIRMED",object,material));
        }
        if(insufficient)return Result.pending("INCOMPLETE");
        return matches.size()==1?matches.values().iterator().next():Result.pending(matches.isEmpty()?"NOT_FOUND":"AMBIGUOUS");
    }
}
