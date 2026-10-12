package com.lynx.ad.modules.delivery.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
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
    public PlanTransferGateway(CreationQianchuanGateway creation,AdListPort list,LynxAdProperties properties,ObjectMapper json) {
        this.creation=creation;this.list=list;this.properties=properties;this.json=json;
    }
    public Credential credential(AdPlan p) { return creation.credential(p.tenantId(),p.accountId().value(),p.awemeId().value()); }
    public JsonNode detail(AdPlan p) { return creation.detail(p.tenantId(),p.accountId().value(),p.awemeId().value(),p.qcPlanId().orElseThrow().value()); }
    public String create(AdPlan p,JsonNode payload) { return creation.create(p.tenantId(),p.accountId().value(),p.awemeId().value(),payload); }
    public void status(Credential c,String id,boolean enabled) {
        var body=json.createObjectNode();body.put("advertiser_id",CreationQianchuanGateway.numericId(c.advertiserId().value()));
        body.putArray("ad_ids").add(CreationQianchuanGateway.numericId(id));body.put("opt_status",enabled?"ENABLE":"DISABLE");
        var result=creation.write(c,properties.getQianchuan().getDelivery().getAdStatusUrl(),body);
        for(var row:result.path("results"))if(id.equals(row.path("ad_id").asText())) {
            if(!row.path("flag").isBoolean())break;
            if(!row.path("flag").asBoolean())throw new BizException(1406,row.path("error").path("error_message").asText("千川拒绝启停计划"));
            return;
        }
        throw new BizException(1523,"千川未确认启停结果，请核查转投结果");
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
}
