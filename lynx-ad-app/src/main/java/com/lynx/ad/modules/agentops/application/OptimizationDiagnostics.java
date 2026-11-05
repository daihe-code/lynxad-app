package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Same-object observations, not causal claims. Missing fields restrict only their dependants. */
public final class OptimizationDiagnostics {
    private OptimizationDiagnostics() {}
    public static void enrich(ObjectNode report, OptimizationRules rules, MonitoringStrategy.Parameters strategy) {
        report.put("strategy_version", strategy.version()).put("strategy_approved", strategy.approved());
        var findings = report.putArray("diagnostics");
        var coverage = report.putArray("coverage_gaps");
        if (!strategy.approved()) coverage.add("经营判断策略尚待校准，候选诊断仅供核对");
        var recent = aggregate(report.path("roi_windows"), strategy, false);
        var baseline = aggregate(report.path("baseline_windows"), strategy, false);
        if (recent == null || baseline == null) coverage.add("缺少同口径的完整成熟对比窗口");
        else {compare(findings, "PLAN", report.path("plan_id").asText(), null, recent, baseline, rules, strategy);refund(findings,report,recent,baseline,rules,strategy);}
        var entities = report.path("diagnostic_context").path("entities");
        if (entities.isEmpty()) coverage.add("商品及计划内素材分解数据暂不可用");
        BigDecimal total = BigDecimal.ZERO, largest = BigDecimal.ZERO; JsonNode leading = null;
        for (var entity : entities) {
            var days = new ArrayList<JsonNode>(); entity.path("days").forEach(days::add);
            days.sort(Comparator.comparing(n -> n.path("date").asText()));
            if (days.size() < 4) continue;
            // Each side contains two distinct completed days; never mix asset-library totals into a plan.
            var a = aggregate(days.subList(days.size()-2, days.size()), strategy, true);
            var b = aggregate(days.subList(days.size()-4, days.size()-2), strategy, true);
            if (a == null || b == null || !report.path("state").asText().equals("READY")) continue;
            if(strategy.approved()&&report.path("state").asText().equals("READY")&&report.path("diagnostic_context").path("complete").asBoolean()
                    &&"MATERIAL".equals(entity.path("kind").asText())&&a.cost.compareTo(strategy.minSpend())>=0&&b.cost.compareTo(strategy.minSpend())>=0
                    &&divide(a.net,a.cost).compareTo(rules.roiFloor())>=0&&divide(b.net,b.cost).compareTo(rules.roiFloor())>=0){
                var n=((ArrayNode)report.path("recommendations")).addObject().put("type","GROWTH_CREATIVE").put("title","保留已验证素材，测试单一新变化")
                    .put("object_id",entity.path("id").asText()).put("object_type","MATERIAL").put("workflow","materials")
                    .put("evidence","同计划素材 "+entity.path("id").asText()+" 在前后两个完成日窗口均达到本计划ROI要求；"+creativeHypothesis(entity))
                    .put("requires_confirmation",true).put("execution_allowed",false);
                n.put("product_id",entity.path("product_id").asText());
            }
            compare(findings, entity.path("kind").asText(), entity.path("id").asText(), entity, a, b, rules, strategy);
            if ("MATERIAL".equals(entity.path("kind").asText())) {
                total = total.add(a.cost); if (a.cost.compareTo(largest) > 0) { largest=a.cost; leading=entity; }
            }
        }
        if (leading != null && total.signum() > 0) {
            var concentration=report.putObject("material_concentration");
            concentration.put("material_id",leading.path("id").asText()).put("spend_share",divide(largest,total).toPlainString());
            // Concentration is a fact, not proof of fatigue or a reason to remove a leading creative.
            concentration.put("basis","同计划、同完成日素材消耗份额，不代表创意疲劳的因果证明");
        }
        if (!report.path("diagnostic_context").path("complete").asBoolean()) coverage.add("商品可投性或独立任务风险尚未核实，暂缓新增投入");
        boolean sampleReady=recent!=null&&recent.cost.compareTo(strategy.minSpend())>=0;
        boolean contextReady=report.path("diagnostic_context").path("complete").asBoolean();
        boolean diagnosticsReady=strategy.approved() && sampleReady && report.path("state").asText().equals("READY");
        boolean growthReady=diagnosticsReady && contextReady && strategy.riskModelApproved() && report.path("diagnostic_context").path("ledger_verified").asBoolean();
        report.put("diagnostics_ready",diagnosticsReady).put("growth_ready",growthReady).put("business_ready",diagnosticsReady&&growthReady);
        if(!growthReady)coverage.add("新增投入账本、独立任务风险或预算瓶颈尚未完整核实");
        var allAdvice=(ArrayNode)report.path("recommendations");
        for(int i=allAdvice.size()-1;i>=0;i--)if(allAdvice.get(i).path("type").asText().equals("ROI_PROTECTION")&&(!strategy.approved()||!sampleReady))allAdvice.remove(i);
        var grouped=new LinkedHashMap<String,ObjectNode>();
        for(var finding:findings)if(!finding.path("shadow").asBoolean()){
            String key=finding.path("object_type").asText()+":"+finding.path("object_id").asText();
            var current=grouped.get(key);
            if(current==null)grouped.put(key,((ObjectNode)finding).deepCopy());
            else current.put("evidence",current.path("evidence").asText()+"；"+finding.path("title").asText());
        }
        if(grouped.values().stream().anyMatch(n->n.path("object_type").asText().equals("PLAN")))
            for(int i=allAdvice.size()-1;i>=0;i--)if(allAdvice.get(i).path("type").asText().equals("ROI_PROTECTION"))allAdvice.remove(i);
        for (var finding : grouped.values()) {
            var advice=((ArrayNode)report.path("recommendations")).addObject();
            advice.put("type",finding.path("code").asText()).put("title",finding.path("title").asText())
                .put("evidence",finding.path("evidence").asText()).put("requires_confirmation",true).put("execution_allowed",false);
            advice.put("object_id",finding.path("object_id").asText()).put("object_type",finding.path("object_type").asText());
            advice.put("workflow",finding.path("workflow").asText());
        }
        for(var item:report.path("recommendations"))if(!item.hasNonNull("action")&&!item.hasNonNull("workflow"))((ObjectNode)item).put("workflow","data");
        // High ROI alone is not a budget bottleneck. Only explicit platform evidence can create a proposal.
        var recommendations=(ArrayNode)report.path("recommendations");
        for (int i=recommendations.size()-1;i>=0;i--) if (recommendations.get(i).path("type").asText().equals("GROWTH_REVIEW")) recommendations.remove(i);
        growth(report,rules,strategy);
    }
    private static void refund(ArrayNode out,JsonNode report,Metrics a,Metrics b,OptimizationRules rules,MonitoringStrategy.Parameters s){
        var recent=report.path("roi_windows");var prior=report.path("baseline_windows");
        if(recent.size()!=prior.size()||recent.size()<s.windowHours()||!report.path("metric_fields").hasNonNull("refundRate"))return;
        boolean increased=true;for(int i=0;i<recent.size();i++){
            var current=decimal(recent.get(i).path("metrics"),"refund_rate");var old=decimal(prior.get(i).path("metrics"),"refund_rate");
            var orders=decimal(recent.get(i).path("metrics"),"orders");var beforeOrders=decimal(prior.get(i).path("metrics"),"orders");
            if(current==null||old==null||current.signum()<0||old.signum()<=0||orders==null||beforeOrders==null||orders.compareTo(BigDecimal.valueOf(s.minOrders()))<0||beforeOrders.compareTo(BigDecimal.valueOf(s.minOrders()))<0||current.compareTo(old.multiply(BigDecimal.ONE.add(s.declineRatio())))<0)increased=false;
        }
        if(increased&&divide(a.net,a.cost).compareTo(rules.roiFloor())<0)finding(out,"E4-07","同口径1小时退款率持续上升，净成交表现承压","PLAN",report.path("plan_id").asText(),null,a,b,"核对对应商品、规格和素材承诺；短期退款指标不代表最终售后结果或利润","data",s);
    }
    private record Metrics(BigDecimal cost,BigDecimal net,BigDecimal orders,BigDecimal impressions,BigDecimal clicks,BigDecimal refund) {}
    private static Metrics aggregate(JsonNode rows,MonitoringStrategy.Parameters s,boolean daily) {
        var values=new ArrayList<JsonNode>();rows.forEach(values::add);return aggregate(values,s,daily);
    }
    private static Metrics aggregate(List<JsonNode> rows,MonitoringStrategy.Parameters s,boolean daily) {
        if(rows.size() < (daily?2:s.windowHours()))return null;
        var sums=new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO};
        boolean[] present={true,true,true,true,true,true};String previous=null;
        for(var row:rows){
            try {
                String start=daily?row.path("date").asText():row.path("window_start").asText();
                String end=daily?LocalDate.parse(start).plusDays(1).toString():row.path("window_end").asText();
                if(previous!=null&&!previous.equals(start)||!daily&&!row.path("complete").asBoolean())return null;
                if(!daily && !Duration.between(OffsetDateTime.parse(start),OffsetDateTime.parse(end)).equals(Duration.ofHours(1)))return null;
                previous=end;
            }catch(RuntimeException e){return null;}
            int i=0;for(String key:List.of("spend","net_revenue","orders","impressions","clicks","refund_rate")) {
                var value=decimal(row.path("metrics"),key);if(value==null||value.signum()<0)present[i]=false;else sums[i]=sums[i].add(value);i++;
            }
        }
        if(!present[0]||!present[1]||sums[0].signum()<=0)return null;
        return new Metrics(sums[0],sums[1],present[2]?sums[2]:null,present[3]?sums[3]:null,present[4]?sums[4]:null,null);
        // Refund rates cannot be summed/averaged without matching denominators and attribution metadata.
    }
    private static void compare(ArrayNode out,String kind,String id,JsonNode entity,Metrics a,Metrics b,OptimizationRules r,MonitoringStrategy.Parameters s) {
        if(a.cost.compareTo(s.minSpend())<0||b.cost.compareTo(s.minSpend())<0)return;
        var roi=divide(a.net,a.cost);var prior=divide(b.net,b.cost);boolean weak=roi.compareTo(r.roiFloor())<0;
        if(a.net.signum()==0)finding(out,"E4-03","成熟消耗尚无净成交",kind,id,entity,a,b,"核对商品可买状态、规格、详情与素材承诺，先限制新增投入","data",s);
        if(falls(roi,prior,s))finding(out,"E4-02",weak?"净成交ROI走弱且低于要求":"净成交ROI走弱，当前仍达到要求",kind,id,entity,a,b,"结合流量成本、点击和商品承接继续核对","data",s);
        var ctr=ratio(a.clicks,a.impressions,s.minImpressions());var oldCtr=ratio(b.clicks,b.impressions,s.minImpressions());
        if(falls(ctr,oldCtr,s))finding(out,"E4-04","点击吸引力走弱",kind,id,entity,a,b,creativeHypothesis(entity),"materials",s);
        // 'orders' is overall paid orders in the present source. Never label this as net-order CVR.
        var cvr=ratio(a.orders,a.clicks,s.minClicks());var oldCvr=ratio(b.orders,b.clicks,s.minClicks());
        if(ctr!=null&&oldCtr!=null&&!falls(ctr,oldCtr,s)&&falls(cvr,oldCvr,s))finding(out,"E4-05","点击相对稳定，支付订单承接下降",kind,id,entity,a,b,"优先核对规格库存、价格优惠、商品详情及素材承诺；支付订单转化不等于净成交转化","data",s);
        var cpc=ratio(a.cost,a.clicks,s.minClicks());var oldCpc=ratio(b.cost,b.clicks,s.minClicks());
        if(cpc!=null&&oldCpc!=null&&oldCpc.signum()>0&&cpc.compareTo(oldCpc.multiply(BigDecimal.ONE.add(s.declineRatio())))>=0)
            finding(out,"E4-06",weak?"点击成本上升，净成交效率承压":"点击成本上升，净成交ROI仍达标",kind,id,entity,a,b,weak?"结合创意吸引力与商品承接核对，暂缓追加投入":"保持观察，不因成本单项变化停投","data",s);
        if("MATERIAL".equals(kind)&&weak&&falls(ctr,oldCtr,s)&&falls(roi,prior,s))finding(out,"E4-08","主力素材存在疲劳线索",kind,id,entity,a,b,creativeHypothesis(entity)+"；保留原素材作为对照，核对流量分配及价格变化","materials",s);
    }
    private static String creativeHypothesis(JsonNode entity){
        String type=entity==null?"":entity.path("material_type").asText();
        return switch(type){case "VIDEO"->"测试新的前3秒商品演示，保留原卖点与主体画面，其余条件尽量一致";case "IMAGE"->"测试主视觉中商品细节和单一卖点的呈现，保留原图作对照";case "CAROUSEL"->"测试首图与图片顺序，使整套图文围绕同一卖点展开";default->"定位对应素材与商品后，单独测试开头、视觉或图文顺序，保留原版本作对照";};
    }
    private static void finding(ArrayNode out,String code,String title,String kind,String id,JsonNode entity,Metrics a,Metrics b,String hypothesis,String workflow,MonitoringStrategy.Parameters s){
        var n=out.addObject().put("code",code).put("title",title).put("object_type",kind).put("object_id",id).put("shadow",!s.approved())
                .put("certainty","OBSERVED_CHANGE").put("workflow",workflow).put("hypothesis",hypothesis);
        if(entity!=null)n.put("product_id",entity.path("product_id").asText()).put("material_type",entity.path("material_type").asText());
        var evidence="同对象可比成熟窗口：净成交ROI "+divide(b.net,b.cost).setScale(2,RoundingMode.HALF_UP)+" → "+divide(a.net,a.cost).setScale(2,RoundingMode.HALF_UP)+"；消耗 ¥"+b.cost+" → ¥"+a.cost+"。";
        n.put("evidence",evidence+hypothesis);n.set("before",metrics(b));n.set("after",metrics(a));
    }
    private static ObjectNode metrics(Metrics m){var n=JsonNodeFactory.instance.objectNode();n.put("spend",m.cost.toPlainString()).put("net_revenue",m.net.toPlainString());if(m.impressions!=null)n.put("impressions",m.impressions.toPlainString());if(m.clicks!=null)n.put("clicks",m.clicks.toPlainString());if(m.orders!=null)n.put("paid_orders",m.orders.toPlainString());return n;}
    private static void growth(ObjectNode report,OptimizationRules r,MonitoringStrategy.Parameters s){
        var advice=(ArrayNode)report.path("recommendations");
        boolean risk=false;for(var item:advice)if(!item.path("type").asText().startsWith("GROWTH"))risk=true;
        if(risk||!report.path("diagnostics").isEmpty())for(int i=advice.size()-1;i>=0;i--)if(advice.get(i).path("type").asText().startsWith("GROWTH"))advice.remove(i);
        if(!s.approved()||!s.riskModelApproved()||!report.path("business_ready").asBoolean()||!report.path("diagnostic_context").path("complete").asBoolean())return;
        if(!report.path("recommendations").isEmpty()||!report.path("diagnostics").isEmpty()||report.path("execution_pending").asBoolean())return;
        var roi=decimal(report,"actual_net_roi");if(roi==null||roi.compareTo(r.roiFloor())<0)return;
        var context=report.path("diagnostic_context");
        if(!context.path("budget_constrained").asBoolean()||!context.path("ledger_verified").asBoolean())return;
        var budget=decimal(report,"daily_budget");var headroom=decimal(context,"headroom");var used=decimal(context,"addition_committed");
        if(budget==null||headroom==null||used==null)return;
        var amount=r.maxAddition().min(r.dailyAddition().subtract(used)).min(headroom).setScale(2,RoundingMode.DOWN);
        if(amount.signum()<=0)return;
        var n=((ArrayNode)report.path("recommendations")).addObject().put("type","GROWTH_BUDGET").put("title","预算限制健康交付，可确认小额调整")
                .put("evidence","平台已返回预算限制，成熟净成交ROI达到本计划要求。预算 "+budget+" → "+budget.add(amount)+"，新增承诺 ¥"+amount+"。").put("requires_confirmation",true).put("execution_allowed",false);
        n.putObject("action").put("kind","BUDGET").put("expected",budget.toPlainString()).put("target",budget.add(amount).toPlainString()).put("cause","MERCHANT_GROWTH");
    }
    static BigDecimal decimal(JsonNode node,String key){try{return node.hasNonNull(key)?new BigDecimal(node.path(key).asText()):null;}catch(RuntimeException e){return null;}}
    private static BigDecimal ratio(BigDecimal numerator,BigDecimal denominator,long min){return numerator==null||denominator==null||denominator.signum()<=0||denominator.compareTo(BigDecimal.valueOf(min))<0?null:divide(numerator,denominator);}
    private static BigDecimal divide(BigDecimal a,BigDecimal b){return a.divide(b,8,RoundingMode.HALF_UP);}
    private static boolean falls(BigDecimal a,BigDecimal b,MonitoringStrategy.Parameters s){return a!=null&&b!=null&&b.signum()>0&&a.compareTo(b.multiply(BigDecimal.ONE.subtract(s.declineRatio())))<=0;}
}
