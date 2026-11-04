package com.lynx.ad.modules.agentops.application;

import com.lynx.ad.common.exception.BizException;
import java.math.BigDecimal;
import java.util.*;

/** Ratios include goods, fulfilment, platform and other variable costs before ad spend. */
public record OptimizationPolicy(boolean enabled,String mode,BigDecimal dailySpendLimit,BigDecimal maxLoss,
        BigDecimal minSpend,int minOrders,int persistenceWindows,int conversionDelayMinutes,List<Economics> economics,Automation automation,Tactics tactics,CreativeGeneration creativeGeneration,OptimizationRules rules) {
    public OptimizationPolicy {
        if(rules!=null){
            rules.validate();
            mode=OptimizationRules.AUTO_PAUSE.equals(rules.protectionAction())?"AUTO_LIMITED":"OBSERVE";
            dailySpendLimit=rules.spendProtection();
            automation=new Automation(false,false,OptimizationRules.AUTO_PAUSE.equals(rules.protectionAction()),false,new BigDecimal("50"),new BigDecimal("500"),new BigDecimal("0.01"),new BigDecimal("100"),BigDecimal.TEN,120,3,9);
            tactics=Tactics.defaults();creativeGeneration=CreativeGeneration.defaults();
            economics=List.of();
        }
        if(automation==null)automation=Automation.defaults();if(tactics==null)tactics=Tactics.defaults();if(creativeGeneration==null)creativeGeneration=CreativeGeneration.defaults();tactics=new Tactics(tactics.boost(),tactics.materialFollow(),creativeGeneration.enabled(),tactics.materialRemove(),false,tactics.productRemove(),tactics.taskBudget(),tactics.taskHours(),tactics.extraBudgetPerDay(),tactics.entityDays(),tactics.retainProducts(),tactics.retainMaterials(),List.of());
    }
    public OptimizationPolicy(boolean enabled,String mode,BigDecimal limit,BigDecimal loss,BigDecimal spend,int orders,int windows,int delay,List<Economics> economics,Automation automation,Tactics tactics,CreativeGeneration generation){this(enabled,mode,limit,loss,spend,orders,windows,delay,economics,automation,tactics,generation,null);}
    public OptimizationPolicy(boolean enabled,String mode,BigDecimal limit,BigDecimal loss,BigDecimal spend,int orders,int windows,int delay,List<Economics> economics,Automation automation,Tactics tactics){this(enabled,mode,limit,loss,spend,orders,windows,delay,economics,automation,tactics,CreativeGeneration.defaults());}
    public OptimizationPolicy(boolean enabled,String mode,BigDecimal limit,BigDecimal loss,BigDecimal spend,int orders,int windows,int delay,List<Economics> economics,Automation automation) {
        this(enabled,mode,limit,loss,spend,orders,windows,delay,economics,automation,Tactics.defaults());
    }
    public OptimizationPolicy(boolean enabled,String mode,BigDecimal limit,BigDecimal loss,BigDecimal spend,int orders,int windows,int delay,List<Economics> economics) {
        this(enabled,mode,limit,loss,spend,orders,windows,delay,economics,Automation.defaults());
    }
    public record Automation(boolean budget,boolean roi,boolean pause,boolean resumeNextDay,BigDecimal minBudget,BigDecimal maxBudget,
            BigDecimal minRoi,BigDecimal maxRoi,BigDecimal maxStepPercent,int cooldownMinutes,int maxActionsPerDay,int resumeHour) {
        public static Automation defaults(){return new Automation(true,false,true,false,new BigDecimal("50"),new BigDecimal("500"),new BigDecimal("0.01"),new BigDecimal("100"),BigDecimal.TEN,120,3,9);}
        public Map<String,Object> engine(){return Map.ofEntries(Map.entry("budget",budget),Map.entry("roi",roi),Map.entry("pause",pause),Map.entry("resume_next_day",resumeNextDay),Map.entry("min_budget",minBudget.toPlainString()),Map.entry("max_budget",maxBudget.toPlainString()),Map.entry("min_roi",minRoi.toPlainString()),Map.entry("max_roi",maxRoi.toPlainString()),Map.entry("max_step_percent",maxStepPercent.toPlainString()),Map.entry("cooldown_minutes",cooldownMinutes),Map.entry("max_actions_per_day",maxActionsPerDay),Map.entry("resume_hour",resumeHour));}
    }
    public record Economics(String productId,BigDecimal contributionMarginRate,BigDecimal refundReserveRate,boolean verified) {}
    public record CreativeGeneration(boolean enabled,boolean autoPublish,List<String> formats,int minMaterials,int maxPending,
            BigDecimal dailyCostLimit,BigDecimal perAssetCostLimit,BigDecimal trialLossLimit,int minImpressions,int minClicks,String brandStyle,String forbiddenClaims,String authorizedBy) {
        public static CreativeGeneration defaults(){return new CreativeGeneration(false,false,List.of("video","image_text","product_card"),2,2,new BigDecimal("100"),new BigDecimal("50"),new BigDecimal("50"),1000,50,"","",null);}
        public Map<String,Object> engine(){return Map.ofEntries(Map.entry("enabled",enabled),Map.entry("auto_publish",autoPublish),Map.entry("formats",formats),Map.entry("min_materials",minMaterials),Map.entry("max_pending",maxPending),Map.entry("daily_cost_limit",dailyCostLimit.toPlainString()),Map.entry("per_asset_cost_limit",perAssetCostLimit.toPlainString()),Map.entry("trial_loss_limit",trialLossLimit.toPlainString()),Map.entry("min_impressions",minImpressions),Map.entry("min_clicks",minClicks),Map.entry("brand_style",brandStyle),Map.entry("forbidden_claims",forbiddenClaims));}
        public CreativeGeneration authorized(String user){return new CreativeGeneration(enabled,autoPublish,formats,minMaterials,maxPending,dailyCostLimit,perAssetCostLimit,trialLossLimit,minImpressions,minClicks,brandStyle,forbiddenClaims,user);}
    }
    public OptimizationPolicy authorized(String user){return new OptimizationPolicy(enabled,mode,dailySpendLimit,maxLoss,minSpend,minOrders,persistenceWindows,conversionDelayMinutes,economics,automation,tactics,creativeGeneration.authorized(user),rules);}
    public record Tactics(boolean boost,boolean materialFollow,boolean materialAdd,boolean materialRemove,boolean productAdd,boolean productRemove,
            BigDecimal taskBudget,BigDecimal taskHours,BigDecimal extraBudgetPerDay,int entityDays,int retainProducts,int retainMaterials,List<String> candidatePlanIds) {
        // Retain the JSON field for old saved policies, but never authorize automatic product addition.
        public Tactics {productAdd=false;candidatePlanIds=candidatePlanIds==null?List.of():List.copyOf(candidatePlanIds);}
        public static Tactics defaults(){return new Tactics(false,false,false,false,false,false,new BigDecimal("100"),BigDecimal.ONE,new BigDecimal("300"),2,1,1,List.of());}
        public boolean active(){return boost||materialFollow||materialAdd||materialRemove||productRemove;}
        public boolean content(){return materialAdd||materialRemove||productRemove;}
        public Map<String,Object> engine(){return Map.ofEntries(Map.entry("boost",boost),Map.entry("material_follow",materialFollow),Map.entry("material_add",materialAdd),Map.entry("material_remove",materialRemove),Map.entry("product_add",productAdd),Map.entry("product_remove",productRemove),Map.entry("task_budget",taskBudget.toPlainString()),Map.entry("task_hours",taskHours.toPlainString()),Map.entry("extra_budget_per_day",extraBudgetPerDay.toPlainString()),Map.entry("entity_days",entityDays),Map.entry("retain_products",retainProducts),Map.entry("retain_materials",retainMaterials),Map.entry("candidate_plan_ids",candidatePlanIds));}
    }
    public static OptimizationPolicy defaults() {
        return new OptimizationPolicy(false,"OBSERVE",new BigDecimal("500"),new BigDecimal("200"),
                new BigDecimal("100"),3,3,60,List.of());
    }
    public void validate(Set<String> products) {
        if(rules!=null){rules.validate();return;}
        if(!Set.of("OBSERVE","AUTO_LIMITED").contains(mode==null?"":mode))throw new BizException(1802,"请选择观察模式或自动调控");
        money(dailySpendLimit,"每日消耗限额",new BigDecimal("0.01"));money(maxLoss,"亏损警戒线",new BigDecimal("0.01"));money(minSpend,"最低样本消耗",BigDecimal.TEN);
        if(minOrders<1 || minOrders>10000 || persistenceWindows<3 || persistenceWindows>8 || conversionDelayMinutes<60 || conversionDelayMinutes>1440)
            throw new BizException(1802,"连续窗口须为3–8小时，转化等待须为60–1440分钟，最低成交数须为1–10000");
        if(economics==null || economics.size()>100)throw new BizException(1802,"商品利润资料不正确");
        Set<String> seen=new HashSet<>();
        for(var e:economics) {
            if(e==null || !products.contains(e.productId()) || !seen.add(e.productId()))throw new BizException(1802,"利润资料必须对应当前计划商品，不能重复");
            rate(e.contributionMarginRate(),true);rate(e.refundReserveRate(),false);
        }
        var a=automation;money(a.minBudget(),"自动预算下限",new BigDecimal("50"));money(a.maxBudget(),"自动预算上限",new BigDecimal("50"));
        money(a.minRoi(),"自动ROI下限",new BigDecimal("0.01"));money(a.maxRoi(),"自动ROI上限",new BigDecimal("0.01"));money(a.maxStepPercent(),"单次调整比例",BigDecimal.ONE);
        if(a.minBudget().compareTo(a.maxBudget())>0 || a.minRoi().compareTo(a.maxRoi())>0 || a.maxRoi().compareTo(new BigDecimal("100"))>0 || a.maxStepPercent().compareTo(new BigDecimal("20"))>0 || a.cooldownMinutes()<60 || a.cooldownMinutes()>1440 || a.maxActionsPerDay()<1 || a.maxActionsPerDay()>10 || a.resumeHour()<0 || a.resumeHour()>23)throw new BizException(1802,"自动调控范围、冷却时间或每日动作次数不正确");
        if("AUTO_LIMITED".equals(mode) && (economics.size()!=products.size() || economics.stream().anyMatch(e->!e.verified())))throw new BizException(1802,"自动调控须先核实全部商品的贡献利润资料");
        var t=tactics;money(t.taskBudget(),"自动调控任务预算",new BigDecimal("100"));money(t.extraBudgetPerDay(),"每日追加预算上限",new BigDecimal("100"));
        if(t.taskHours()==null || t.taskHours().compareTo(new BigDecimal(".5"))<0 || t.taskHours().compareTo(new BigDecimal("24"))>0 || t.taskHours().remainder(new BigDecimal(".5")).signum()!=0 || t.taskBudget().compareTo(t.extraBudgetPerDay())>0 || t.entityDays()<2 || t.entityDays()>7 || t.retainProducts()<1 || t.retainProducts()>100 || t.retainMaterials()<1 || t.retainMaterials()>100 || t.candidatePlanIds().size()>3 || new HashSet<>(t.candidatePlanIds()).size()!=t.candidatePlanIds().size() || t.candidatePlanIds().stream().anyMatch(id->id==null||id.isBlank()||id.length()>64))throw new BizException(1802,"新增动作授权范围不正确；候选计划最多3个，素材与商品至少保留1个");
        var g=creativeGeneration;money(g.dailyCostLimit(),"每日生成预算预留上限",new BigDecimal("0.01"));money(g.perAssetCostLimit(),"单条生成预算预留上限",new BigDecimal("0.01"));money(g.trialLossLimit(),"单条试投亏损上限",new BigDecimal("0.01"));
        if(g.formats()==null||g.formats().isEmpty()||g.formats().size()>3||new HashSet<>(g.formats()).size()!=g.formats().size()||g.formats().stream().anyMatch(v->v==null||!Set.of("video","image_text","product_card").contains(v))||g.minMaterials()<1||g.minMaterials()>20||g.maxPending()<1||g.maxPending()>2||g.perAssetCostLimit().compareTo(g.dailyCostLimit())>0||g.minImpressions()<100||g.minImpressions()>10000000||g.minClicks()<10||g.minClicks()>100000||g.brandStyle()==null||g.brandStyle().length()>300||g.forbiddenClaims()==null||g.forbiddenClaims().length()>500)throw new BizException(1802,"自动生成形式、数量、预算或风格约束不正确");
    }
    private static void money(BigDecimal value,String label,BigDecimal min) {
        if(value==null || value.compareTo(min)<0 || value.compareTo(new BigDecimal("999999999"))>0 || value.stripTrailingZeros().scale()>2)
            throw new BizException(1802,label+"超出范围或超过两位小数");
    }
    private static void rate(BigDecimal value,boolean oneAllowed) {
        if(value==null || value.signum()<0 || value.compareTo(BigDecimal.ONE)>0 || (!oneAllowed && value.compareTo(BigDecimal.ONE)==0) || value.stripTrailingZeros().scale()>8)
            throw new BizException(1802,"贡献率应在0–100%之间，额外退款预留应在0–100%之间且不含100%");
    }
    public OptimizationPolicy withEconomics(List<Economics> values) {
        return new OptimizationPolicy(enabled,mode,dailySpendLimit,maxLoss,minSpend,minOrders,persistenceWindows,conversionDelayMinutes,values,automation,tactics,creativeGeneration,rules);
    }
    public Map<String,Object> enginePolicy() {
        return Map.ofEntries(Map.entry("mode",mode),Map.entry("daily_spend_limit",dailySpendLimit.toPlainString()),Map.entry("max_loss",maxLoss.toPlainString()),Map.entry("min_spend",minSpend.toPlainString()),Map.entry("min_orders",minOrders),Map.entry("persistence_windows",persistenceWindows),Map.entry("conversion_delay_minutes",conversionDelayMinutes),Map.entry("execution",automation.engine()),Map.entry("tactics",tactics.engine()),Map.entry("creative_generation",creativeGeneration.engine()));
    }
}
