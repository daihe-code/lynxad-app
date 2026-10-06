package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.*;
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
    @Override public List<JsonNode> materials(Credential c,String id) { return List.of(); }
    @Override public String control(Credential c,String id,String scene,String name,BigDecimal budget,BigDecimal duration,List<String> materials) {
        String taskId="mock_task_"+UUID.randomUUID().toString().substring(0,8);
        var row=json.createObjectNode().put("id",taskId).put("name",name).put("scene",scene).put("task_status","MOCK")
                .put("budget",budget).put("duration",duration);
        tasks.computeIfAbsent(key(c,id),k->Collections.synchronizedList(new ArrayList<>())).add(row);
        return taskId;
    }
    @Override public List<JsonNode> controls(Credential c,String id) { return List.copyOf(tasks.getOrDefault(key(c,id),List.of())); }
}
