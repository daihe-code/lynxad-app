package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.*;

/** Keep missing outputs separate from stored media: a failed slot must never enter upload or publish. */
public final class GenerationIssueMapper {
    private GenerationIssueMapper() {}
    private static final Set<String> FAILED=Set.of("failed","partial_failed","cancelled");
    private static final Map<String,String> KEYS=Map.of("VIDEO","videos","IMAGE","product_cards","CAROUSEL","image_texts");
    private static final Map<String,String> LABELS=Map.of("VIDEO","视频","IMAGE","商品卡图片","CAROUSEL","图文");
    public static String type(String value) {
        return switch(value){case "video"->"VIDEO";case "product_card"->"IMAGE";case "image_text"->"CAROUSEL";default->null;};
    }
    public static String message(JsonNode error,String fallback) {
        String value=error.isTextual()?error.asText():error.path("message").asText(error.path("detail").asText(""));
        return value.isBlank()?fallback:value;
    }
    public static List<CreationState.GenerationIssue> requested(CreationState state,String reason) {
        var r=state.regeneration;
        if(r!=null)for(var material:state.materials)if(Objects.equals(material.id,r.sourceId)) {
            var product=state.products.stream().filter(p->p.qcProductId().equals(material.productId)).findFirst().orElse(null);
            return List.of(new CreationState.GenerationIssue(material.productId,product==null?material.productId:product.name(),reason,
                    material.type,null,Objects.toString(material.title,"素材")+"（重新生成）","revision:"+r.requestId));
        }
        return state.products.stream().filter(p->state.generationProductIds.contains(p.qcProductId()))
                .map(p->new CreationState.GenerationIssue(p.qcProductId(),p.name(),reason)).toList();
    }
    public static List<CreationState.GenerationIssue> from(CreationState state,JsonNode job) {
        var issues=new ArrayList<CreationState.GenerationIssue>();
        for(var group:job.path("groups")) {
            int index;
            try {index=Integer.parseInt(group.path("group_id").asText().replace("group-",""))-1;}
            catch(NumberFormatException ignored){continue;}
            if(index<0||index>=state.products.size())continue;
            var product=state.products.get(index);
            String groupId=group.path("group_id").asText();
            var slots=new LinkedHashMap<String,CreationState.GenerationIssue>();
            var completed=new HashMap<String,Set<String>>();
            for(var item:group.path("materials")) {
                if(!"v1".equals(item.path("version").asText()))continue;
                String type=type(item.path("material_type").asText());if(type==null)continue;
                String mid=item.path("material_id").asText(null);
                if(mid!=null&&state.removedMaterialIds.contains(mid)&&FAILED.contains(item.path("status").asText()))continue;
                String slot=item.path("detail").path("set_id").asText(item.path("material_slot_id").asText(mid==null?type:mid));
                if("succeeded".equals(item.path("status").asText())) {
                    String family=item.path("detail").path("delivery_family_id").asText(slot);
                    completed.computeIfAbsent(type,k->new HashSet<>()).add(family);continue;
                }
                if(!FAILED.contains(item.path("status").asText()))continue;
                String reason=message(item.path("detail").path("error"),"本条素材生成未完成，引擎未返回具体原因");
                slots.put(type+":"+slot,new CreationState.GenerationIssue(product.qcProductId(),product.name(),reason,type,mid,
                        item.path("content").path("title").asText(LABELS.get(type)+"生成失败"),slot));
            }
            for(var failure:group.path("generation_failures")) {
                String type=type(failure.path("material_type").asText());if(type==null)continue;
                String slot=failure.path("slot_id").asText();if(slot.isBlank())continue;
                var old=slots.get(type+":"+slot);
                String reason=message(failure.path("message"),"本条素材生成未完成，引擎未返回具体原因");
                if(old!=null) {
                    var messages=new LinkedHashSet<>(Arrays.asList(old.message().split("；")));
                    messages.addAll(Arrays.asList(reason.split("；")));reason=String.join("；",messages);
                }
                slots.put(type+":"+slot,new CreationState.GenerationIssue(product.qcProductId(),product.name(),reason,type,
                        old==null?null:old.materialId(),old==null?failure.path("title").asText(LABELS.get(type)+"生成失败"):old.title(),slot));
            }
            boolean terminal=FAILED.contains(job.path("status").asText())||FAILED.contains(group.path("status").asText());
            boolean unfinished=terminal&&!"succeeded".equals(group.path("status").asText());
            if(unfinished)for(String type:List.of("VIDEO","IMAGE","CAROUSEL")) {
                int done=completed.getOrDefault(type,Set.of()).size();
                int failed=(int)slots.values().stream().filter(i->type.equals(i.type())).count();
                int missing=Math.max(0,group.path("expected").path(KEYS.get(type)).asInt()-done-failed);
                for(int n=0;n<missing;n++) {
                    String slot=groupId+":"+type+":missing:"+(done+failed+n+1);
                    // Old engines may report a group error without identifying the failed component.
                    String reason=slots.isEmpty()&&group.path("materials").isEmpty()
                            ?message(group.path("error"),message(job.path("error"),"引擎未返回本条素材的具体失败原因"))
                            :"本条素材未生成可用结果，引擎未返回具体失败原因";
                    slots.put(type+":"+slot,new CreationState.GenerationIssue(product.qcProductId(),product.name(),reason,type,null,
                            LABELS.get(type)+"素材 "+(done+failed+n+1),slot));
                }
            }
            issues.addAll(slots.values());
            if(unfinished&&slots.isEmpty())issues.add(new CreationState.GenerationIssue(product.qcProductId(),product.name(),
                    message(group.path("error"),message(job.path("error"),"商品创意准备未完成，请重试或从素材库补齐"))));
        }
        if(job.path("groups").isEmpty()&&FAILED.contains(job.path("status").asText())) {
            issues.addAll(requested(state,message(job.path("error"),"本次生成任务未完成，请核查任务记录")));
        }
        return issues;
    }
}
