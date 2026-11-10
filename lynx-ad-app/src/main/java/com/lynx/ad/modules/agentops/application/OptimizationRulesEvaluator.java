package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** A bounded history of real cumulative snapshots; no interpolation and no synthetic minute data. */
public final class OptimizationRulesEvaluator {
    private static final ObjectMapper JSON=new ObjectMapper();
    private OptimizationRulesEvaluator() {}
    static boolean planBudgetConstrained(String platformStatus) {
        return "OFFLINE_BUDGET".equals(platformStatus) || "PRE_OFFLINE_BUDGET".equals(platformStatus);
    }
    public static JsonNode evaluate(String payload,JsonNode previous,OptimizationPolicy policy) {
        try{return evaluate(JSON.readTree(payload),previous,policy);}catch(java.io.IOException e){throw new IllegalArgumentException("Invalid rule observation",e);}
    }
    public static JsonNode evaluate(String payload,JsonNode previous,OptimizationPolicy policy,MonitoringStrategy.Parameters parameters) {
        try {
            var data=JSON.readTree(payload);var result=(ObjectNode)evaluate(data,previous,policy,parameters.windowHours(),parameters.maturityMinutes());
            var windows=result.putArray("baseline_windows");
            for(var recent:result.path("roi_windows"))for(var row:data.path("observations")) {
                try { if(OffsetDateTime.parse(recent.path("window_start").asText()).minusDays(1).equals(OffsetDateTime.parse(row.path("window_start").asText())))windows.add(row); }
                catch(RuntimeException ignored){}
            }
            OptimizationDiagnostics.enrich(result,policy.rules(),parameters);return result;
        }catch(java.io.IOException e){throw new IllegalArgumentException("Invalid rule observation",e);}
    }
    public static JsonNode evaluate(JsonNode data,JsonNode previous,OptimizationPolicy policy) {
        return evaluate(data,previous,policy,3,60);
    }
    private static JsonNode evaluate(JsonNode data,JsonNode previous,OptimizationPolicy policy,int windowHours,int maturityMinutes) {
        var rules=Objects.requireNonNull(policy.rules());
        var result=(ObjectNode)data.deepCopy();
        result.put("rule_version","merchant-rules-v2");result.put("source","QIANCHUAN");
        result.put("state","WAITING_DATA");result.put("window_count",0);
        var reasons=result.putArray("reasons");var advice=result.putArray("recommendations");
        var observed=OffsetDateTime.parse(data.path("observed_at").asText());
        var restarted=data.hasNonNull("optimization_restarted_at")
            ?Instant.parse(data.path("optimization_restarted_at").asText()):null;
        var history=result.putArray("rule_samples");
        boolean complete=data.path("complete").asBoolean();
        BigDecimal spend=decimal(data,"daily_spend"),revenue=decimal(data,"daily_net_revenue");
        boolean pending=data.path("execution_pending").asBoolean();
        String platformStatus=data.path("platform_status").asText();
        if(!pending && planBudgetConstrained(platformStatus)) {
            boolean exhausted="OFFLINE_BUDGET".equals(platformStatus);
            advice(advice,"BUDGET_CONSTRAINT",exhausted?"计划预算不足":"计划预算即将用尽",
                "千川已报告该计划的预算限制，可核对预算与当日消耗。")
                .put("requires_confirmation",false).put("workflow","data");
        }
        if(!"DELIVERING".equals(data.path("plan_status").asText())){
            result.put("state","PAUSED");reasons.add("计划未在投放，保持观察");return result;
        }
        if(!data.path("execution_pending").asBoolean() && (spend==null || spend.signum()==0)) {
            var since = zeroDeliverySince(data, previous, observed, restarted);
            if (since != null) {
                result.put("zero_delivery_since", since.toString());
                var checkedAt = OffsetDateTime.parse(data.path("delivery_check").path("checked_at").asText());
                if (!checkedAt.isBefore(since.plusHours(1))) {
                    result.put("state", "NO_DELIVERY");
                    advice(advice, "DELIVERY_NO_ACTIVITY", "持续无曝光", "计划处于投放中，持续至少 1 小时无曝光、无消耗。请核对投放诊断。")
                        .put("requires_confirmation", false);
                }
            }
        }
        if(previous!=null)for(var sample:previous.path("rule_samples")){
            try{var time=OffsetDateTime.parse(sample.path("at").asText());
                if(time.toLocalDate().equals(observed.toLocalDate()) && !time.isAfter(observed) && time.isAfter(observed.minusMinutes(65))
                    && (restarted==null || !time.toInstant().isBefore(restarted)))history.add(sample.deepCopy());
            }catch(RuntimeException ignored){/* Unusable evidence never becomes a baseline. */}
        }
        if(data.path("execution_pending").asBoolean()){reasons.add("正在核实已提交操作，不补执行旧建议");return result;}
        var mature=new ArrayList<JsonNode>();
        for(var window:data.path("observations")){
            try {
                var start=OffsetDateTime.parse(window.path("window_start").asText());
                var end=OffsetDateTime.parse(window.path("window_end").asText());
                if(window.path("complete").asBoolean() && !end.isAfter(observed.minusMinutes(maturityMinutes))
                    && (restarted==null || !start.toInstant().isBefore(restarted)))mature.add(window);
            }catch(RuntimeException ignored){}
        }
        mature.sort(Comparator.comparing(w->w.path("window_end").asText()));
        var windows=result.putArray("roi_windows");BigDecimal matureSpend=BigDecimal.ZERO,matureRevenue=BigDecimal.ZERO;boolean matureReady=mature.size()>=windowHours;
        OffsetDateTime priorEnd=null;
        for(var window:mature.subList(Math.max(0,mature.size()-windowHours),mature.size())){
            var start=OffsetDateTime.parse(window.path("window_start").asText());var end=OffsetDateTime.parse(window.path("window_end").asText());
            var cost=decimal(window.path("metrics"),"spend");var net=decimal(window.path("metrics"),"net_revenue");
            if(priorEnd!=null && !priorEnd.equals(start) || !Duration.between(start,end).equals(Duration.ofHours(1)) || cost==null || net==null || cost.signum()<0 || net.signum()<0)matureReady=false;
            priorEnd=end;if(cost!=null)matureSpend=matureSpend.add(cost);if(net!=null)matureRevenue=matureRevenue.add(net);windows.add(window);
        }
        var expectedEnd=observed.minusMinutes(maturityMinutes).withMinute(0).withSecond(0).withNano(0);
        if(priorEnd==null || !priorEnd.equals(expectedEnd) || matureSpend.signum()<=0)matureReady=false;
        if(matureReady){result.put("state","READY");result.put("actual_net_roi",matureRevenue.divide(matureSpend,8,RoundingMode.HALF_UP).toPlainString());result.put("mature_spend",matureSpend.toPlainString());result.put("mature_net_revenue",matureRevenue.toPlainString());result.put("window_count",windowHours);}
        else reasons.add("等待连续完整小时及成交回补，暂不判断ROI效果");
        var matureRoi=matureReady?matureRevenue.divide(matureSpend,8,RoundingMode.HALF_UP):null;
        if(matureRoi!=null && matureRoi.compareTo(rules.roiFloor())<0)
            advice(advice,"ROI_PROTECTION","净成交ROI低于本计划要求","最近 "+windowHours+" 个完整成熟小时的净成交ROI "+matureRoi.setScale(2,RoundingMode.HALF_UP)+"，期望不低于 "+rules.roiFloor()+"；先核对成交回传与投放变化。");
        if(!complete || spend==null || spend.signum()<0){
            reasons.add("等待完整的当天消耗数据，暂缓消耗保护和新增投入判断");
            return result;
        }
        // One sample per minute; repeated reads cannot create evidence of elapsed time.
        if(history.isEmpty() || !history.get(history.size()-1).path("at").asText().startsWith(observed.toString().substring(0,16))){
            var sample=history.addObject();sample.put("at",observed.toString());sample.put("spend",spend.toPlainString());
            if(revenue!=null && revenue.signum()>=0)sample.put("revenue",revenue.toPlainString());
        }
        if(history.size()>66){var trimmed=JSON.createArrayNode();for(int i=history.size()-66;i<history.size();i++)trimmed.add(history.get(i));result.set("rule_samples",trimmed);history=trimmed;}
        result.put("window_end",observed.toLocalDate().toString()); // Daily deduplication for reminders/advice.
        boolean protection=spend.compareTo(rules.spendProtection())>=0;
        boolean runaway=false;
        if(protection){
            var item=advice(advice,"SPEND_PROTECTION","当天消耗达到保护金额","当天消耗 ¥"+spend.toPlainString()+"，保护金额 ¥"+rules.spendProtection()+"。");
            boolean automatic=OptimizationRules.AUTO_PAUSE.equals(rules.protectionAction());
            item.put("execution_allowed",automatic);item.put("requires_confirmation",!automatic);
            item.putObject("action").put("kind","PAUSE").put("expected","DELIVERING").put("target","PAUSED").put("cause","DAILY_LIMIT");
        }else if(spend.compareTo(rules.spendReminder())>=0)advice(advice,"SPEND_REMINDER","当天消耗达到提醒金额","当天消耗 ¥"+spend.toPlainString()+"，提醒金额 ¥"+rules.spendReminder()+"。");
        if(revenue!=null && revenue.signum()>=0 && spend.signum()>0)
            result.put("cumulative_net_roi",revenue.divide(spend,8,RoundingMode.HALF_UP).toPlainString());
        // Spend acceleration is an independent lead. Intraminute conversion deltas are not mature ROI.
        JsonNode baseline=null;var cutoff=observed.minusMinutes(rules.runawayMinutes());
        for(var sample:history){var at=OffsetDateTime.parse(sample.path("at").asText());if(!at.isAfter(cutoff) && !at.isBefore(cutoff.minusSeconds(90)))baseline=sample;}
        boolean continuous=baseline!=null;OffsetDateTime last=baseline==null?null:OffsetDateTime.parse(baseline.path("at").asText());
        BigDecimal lastSpend=baseline==null?null:decimal(baseline,"spend");
        for(var sample:history){var at=OffsetDateTime.parse(sample.path("at").asText());if(last!=null && at.isAfter(last)){
            var value=decimal(sample,"spend");
            if(Duration.between(last,at).getSeconds()>150||value==null||lastSpend==null||value.compareTo(lastSpend)<0)continuous=false;
            last=at;lastSpend=value;
        }}
        if(continuous && last!=null && Duration.between(last,observed).getSeconds()<=90){
            var oldSpend=decimal(baseline,"spend");
            if(oldSpend!=null && oldSpend.signum()>=0){var delta=spend.subtract(oldSpend);
                if(delta.signum()>0 && delta.compareTo(rules.runawaySpend())>=0){
                    runaway=true;advice(advice,"RUNAWAY_CHECK","消耗加快，成交数据待核对","最近约 "+rules.runawayMinutes()+" 分钟消耗 ¥"+delta+"；继续核对成交回补，尚不能确认跑飞，暂停仍需确认。");
                }
            }
        }
        if(revenue==null)reasons.add("净成交数据未齐，消耗保护继续运行");
        if(!protection && !runaway && spend.compareTo(rules.spendReminder())<0 && matureRoi!=null && matureRoi.compareTo(rules.roiFloor())>=0){
            var item=advice(advice,"GROWTH_REVIEW","可核对是否适合继续增长","已取得成熟净成交窗口，仍需核对预算瓶颈、素材和运行任务。");
            item.put("max_addition",rules.maxAddition().toPlainString()).put("daily_addition",rules.dailyAddition().toPlainString());
        }
        return result;
    }

    private static OffsetDateTime zeroDeliverySince(JsonNode data, JsonNode previous, OffsetDateTime observed, Instant restarted) {
        var check = data.path("delivery_check");
        if (!"ZERO".equals(check.path("activity").asText())) return null;
        var checkedAt = OffsetDateTime.parse(check.path("checked_at").asText());
        if (checkedAt.isAfter(observed) || checkedAt.isBefore(observed.minusMinutes(10))
            || !checkedAt.toLocalDate().equals(observed.toLocalDate())
            || restarted != null && checkedAt.toInstant().isBefore(restarted)) return null;
        if (previous != null && previous.hasNonNull("zero_delivery_since")
            && data.path("plan_id").equals(previous.path("plan_id"))
            && data.path("product_ids").equals(previous.path("product_ids"))) {
            var previousObserved = OffsetDateTime.parse(previous.path("observed_at").asText());
            var since = OffsetDateTime.parse(previous.path("zero_delivery_since").asText());
            if (since.toLocalDate().equals(observed.toLocalDate()) && !since.isAfter(checkedAt)
                && !previousObserved.isAfter(observed) && !previousObserved.isBefore(observed.minusMinutes(5))
                && (restarted == null || !since.toInstant().isBefore(restarted))) return since;
        }
        return checkedAt;
    }

    private static ObjectNode advice(ArrayNode list,String type,String title,String evidence){var item=list.addObject();item.put("type",type).put("title",title).put("evidence",evidence).put("requires_confirmation",true).put("execution_allowed",false);return item;}
    private static BigDecimal decimal(JsonNode n,String key){try{return n.hasNonNull(key)?new BigDecimal(n.get(key).asText()):null;}catch(RuntimeException e){return null;}}
}
