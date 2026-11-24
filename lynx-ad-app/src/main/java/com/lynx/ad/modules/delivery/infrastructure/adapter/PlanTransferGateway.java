package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Transfer writes have no automatic transport retries, including status updates. */
@Component
public class PlanTransferGateway {
    private final CreationQianchuanGateway creation;
    private final AdListPort list;
    private final LynxAdProperties properties;
    private final ObjectMapper json;
    private final AdManagementPort management;
    public PlanTransferGateway(CreationQianchuanGateway creation,AdListPort list,LynxAdProperties properties,ObjectMapper json,AdManagementPort management) {
        this.creation=creation;this.list=list;this.properties=properties;this.json=json;this.management=management;
    }
    public Credential credential(AdPlan p) { return creation.credential(p.tenantId(),p.accountId().value(),p.awemeId().value()); }
    public JsonNode detail(AdPlan p) { return creation.detail(p.tenantId(),p.accountId().value(),p.awemeId().value(),p.qcPlanId().orElseThrow().value()); }
    public JsonNode detail(AdPlan p,String id) { return creation.detail(p.tenantId(),p.accountId().value(),p.awemeId().value(),id); }
    public JsonNode recommendations(AdPlan plan, List<String> productIds, SmartBidType targetMode) {
        return creation.recommendations(plan.tenantId(), plan.accountId().value(), plan.awemeId().value(),
                productIds, targetMode, plan.qcPlanId().orElseThrow().value());
    }
    public String create(AdPlan p,JsonNode payload) { return creation.create(p.tenantId(),p.accountId().value(),p.awemeId().value(),payload); }
    public AdManagementPort.InsightReport history(Credential c,String qcId,java.time.LocalDate start,java.time.LocalDate end) {
        return management.insightReport(c,qcId,start,end,true);
    }
    public void status(Credential c,String id,boolean enabled) {
        changeStatus(c,id,enabled?"ENABLE":"DISABLE");
    }
    public void delete(Credential c,String id) {
        changeStatus(c,id,"DELETE");
    }
    private void changeStatus(Credential c,String id,String action) {
        var body=json.createObjectNode();body.put("advertiser_id",CreationQianchuanGateway.numericId(c.advertiserId().value()));
        body.putArray("ad_ids").add(CreationQianchuanGateway.numericId(id));body.put("opt_status",action);
        var result=creation.write(c,properties.getQianchuan().getDelivery().getAdStatusUrl(),body);
        for(var row:result.path("results"))if(id.equals(row.path("ad_id").asText())) {
            if(!row.path("flag").isBoolean())break;
            if(!row.path("flag").asBoolean())throw new BizException(1406,row.path("error").path("error_message").asText("平台拒绝更新计划状态"));
            return;
        }
        throw new BizException(1523,"平台未确认计划状态，请核查转投结果");
    }
    public void settings(Credential c,String id,BigDecimal budget,BigDecimal roi) {
        var body=json.createObjectNode();body.put("advertiser_id",CreationQianchuanGateway.numericId(c.advertiserId().value()));
        body.put("ad_id",CreationQianchuanGateway.numericId(id));var setting=body.putObject("delivery_setting");if(budget!=null)setting.put("budget",budget);
        if(roi!=null)setting.put("roi2_goal",roi);
        creation.write(c,properties.getQianchuan().getDelivery().getAdUpdateUrl(),body);
    }
    public List<com.lynx.ad.modules.delivery.domain.port.OceanPlanRow> findCreated(Credential c,SmartBidType mode,String name) {
        return list.list(c,mode).stream().filter(row->name.equals(row.name())).toList();
    }
    public void details(Credential c,String id,String name,String scheduleType,java.time.LocalDate endDate) {
        if(name!=null)management.settings(c,id,name,null,null);
        if(scheduleType!=null)management.schedule(c,id,scheduleType,endDate);
    }
}
