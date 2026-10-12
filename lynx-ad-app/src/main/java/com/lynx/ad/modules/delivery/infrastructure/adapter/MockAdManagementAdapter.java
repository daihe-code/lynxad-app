package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="lynx.ad.mock",name="qianchuan",havingValue="true",matchIfMissing=true)
public class MockAdManagementAdapter implements AdManagementPort {
    private final ObjectMapper json;
    private final Map<String,List<JsonNode>> tasks=new ConcurrentHashMap<>();
    public MockAdManagementAdapter(ObjectMapper json) { this.json=json; }
    private String key(Credential c,String id) { return c.advertiserId().value()+":"+id; }
    @Override public JsonNode detail(Credential c,String id) { return json.createObjectNode().put("ad_id",id); }
    @Override public Map<String,String> guarantees(Credential c,List<String> ids) { return Map.of(); }
    @Override public void settings(Credential c,String id,String name,BigDecimal budget,BigDecimal roi) {}
    @Override public List<JsonNode> report(Credential c,LocalDate day) { return List.of(); }
    @Override public List<JsonNode> report(Credential c,LocalDate start,LocalDate end) { return List.of(); }
    @Override public List<JsonNode> materials(Credential c,String id) { return List.of(); }
    @Override public String control(Credential c,String id,String scene,String name,BigDecimal budget,BigDecimal duration,List<String> materials) {
        String taskId="mock_task_"+UUID.randomUUID().toString().substring(0,8);
        var row=json.createObjectNode().put("id",taskId).put("name",name).put("scene",scene).put("task_status","MOCK")
                .put("budget",budget).put("duration",duration);
        tasks.computeIfAbsent(key(c,id),k->Collections.synchronizedList(new ArrayList<>())).add(row);
        return taskId;
    }
    @Override public List<JsonNode> controls(Credential c,String id) { return List.copyOf(tasks.getOrDefault(key(c,id),List.of())); }
    @Override public List<JsonNode> controls(Credential c,String id,String scene,LocalDate start,LocalDate end) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);
        return controls(c,id,scene);
    }
    @Override public void controlStatus(Credential c,String taskId,String operation) {
        for(var entry:tasks.entrySet())if(entry.getKey().startsWith(c.advertiserId().value()+":")) {
            var rows=entry.getValue();synchronized(rows) {
                for(var row:rows)if(taskId.equals(row.path("id").asText())) {
                    if("DELETE".equals(operation))rows.remove(row);
                    else ((ObjectNode)row).put("task_status",switch(operation){case "PAUSE"->"DISABLE";case "ENABLE"->"PROCESSING";case "DISABLE"->"FROZEN";default->throw new BizException(1402,"调控操作不正确");});
                    return;
                }
            }
        }
        throw new BizException(1405,"模拟任务不存在");
    }
    @Override public InsightReport controlReport(Credential c,String adId,String taskId,String scene,LocalDate start,LocalDate end,String granularity) {
        new com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange(start,end);
        return new InsightReport(json.createObjectNode(),List.of(),"HOUR".equals(granularity)?"stat_time_hour":"stat_time_day",Map.of(),"模拟任务暂无真实投放数据");
    }
}
