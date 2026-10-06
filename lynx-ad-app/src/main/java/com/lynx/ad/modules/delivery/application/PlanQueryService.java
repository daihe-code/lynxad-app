package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.api.dto.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.dataops.domain.port.*;
import com.lynx.ad.modules.catalog.domain.port.ProductRepository;
import com.lynx.ad.modules.catalog.domain.model.Product;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlanQueryService {
    private final AdPlanRepository plans;
    private final MetricSnapshotRepository snapshots;
    private final ProductRepository products;
    private final AdPlanCreativeBindingStore bindings;
    private final PlanRemoteStore remote;
    private final OauthAccountRepository accounts;
    private boolean live;
    @Autowired
    public void configuration(com.lynx.ad.common.config.LynxAdProperties properties) { live=!properties.getMock().isQianchuan(); }
    public PlanQueryService(AdPlanRepository plans,AdListPort list,OauthAccountRepository accounts,
                            QianchuanCredentialFacade credentials,MetricSnapshotRepository snapshots,
                            ProductRepository products,AdPlanCreativeBindingStore bindings) {
        this(plans,list,accounts,credentials,snapshots,products,bindings,null);
    }
    @Autowired
    public PlanQueryService(AdPlanRepository plans,AdListPort list,OauthAccountRepository accounts,
                            QianchuanCredentialFacade credentials,MetricSnapshotRepository snapshots,
                            ProductRepository products,AdPlanCreativeBindingStore bindings,PlanRemoteStore remote) {
        this.plans=plans;this.snapshots=snapshots;this.products=products;this.bindings=bindings;this.remote=remote;this.accounts=accounts;
    }
    public record Filters(String keyword,String status,String platform,String bidType,Integer createdWithin,
                          String accountId,String sortField,boolean descending) {}
    public record Summary(BigDecimal cost,BigDecimal gmv,Long orders,BigDecimal roi,int covered,int plans,Instant snapshotTs) {}
    public record PageResult<T>(List<T> records,long total,int pageNum,int pageSize,Summary summary) {
        public PageResult(List<T> records,long total,int pageNum,int pageSize) { this(records,total,pageNum,pageSize,null); }
    }
    public PageResult<PlanListVo> list(TenantId tenant,int pageNum,int pageSize) { return list(tenant,pageNum,pageSize,null); }
    public PageResult<PlanListVo> list(TenantId tenant,int pageNum,int pageSize,Filters filter) {
        if(pageNum<1 || pageSize<1 || pageSize>200) throw new BizException(1402,"分页参数不正确");
        List<PlanListVo> all=rows(tenant,null).stream().filter(row->matches(row,filter)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        sort(all,filter);
        long from=(long)(pageNum-1)*pageSize;
        return new PageResult<>(from>=all.size()?List.of():List.copyOf(all.subList((int)from,(int)Math.min(all.size(),from+pageSize))),
                all.size(),pageNum,pageSize,summary(all));
    }
    public PlanVo get(TenantId tenant,PlanId id) {
        var plan=plans.findById(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在"));
        var row=rows(tenant,plan).getFirst();
        var r=remote==null?null:remote.list(tenant).get(id.value());
        String aweme=plan.awemeId().value();
        if(r!=null && r.detail()!=null && r.detail().hasNonNull("aweme_id")) aweme=r.detail().path("aweme_id").asText();
        return new PlanVo(row.planId(),row.name(),row.status(),row.source(),row.qcPlanId(),row.smartBidType(),row.budget(),aweme,
                plan.rejectReason(),row.lingtou(),row.guarantee(),row.coverUrl(),row.costYuan(),row.gmvYuan(),row.roi(),
                plan.accountId().value(),row.roi2Goal(),row.startDate(),row.endDate(),row.convert(),row.snapshotTs(),row.products(),row.comparison(),
                row.createdAt(),row.deliveryPlatform(),plan.qcStatus(),r==null?null:r.detail());
    }
    private List<PlanListVo> rows(TenantId tenant,AdPlan only) {
        var local=only==null?plans.findByTenant(tenant):List.of(only);
        var meta=plans.listMetadataByTenant(tenant);
        var latest=snapshots.listLatestPairsByTenant(tenant);
        var selected=bindings.productIdsByPlan(local.stream().map(p->p.id().value()).toList());
        var cached=remote==null?Map.<String,PlanRemoteStore.Remote>of():remote.list(tenant);
        Map<String,List<Product>> catalogs=new HashMap<>();
        Set<String> authorized=new HashSet<>();
        for(var account:accounts.findByTenant(tenant)) if("ACTIVE".equals(account.status()) &&
                (account.tokenExpiresAt()==null || account.tokenExpiresAt().isAfter(Instant.now()))) authorized.add(account.id().value());
        List<PlanListVo> result=new ArrayList<>();
        for(var plan:local) {
            var metadata=meta.get(plan.id().value()); var pair=latest.get(plan.id().value()); var snap=pair==null?null:pair.current();
            var r=cached.get(plan.id().value()); JsonNode info=r==null?null:QianchuanPlanMapping.info(r.payload());
            if(live && r==null) snap=null;
            JsonNode setting=r==null?null:QianchuanPlanMapping.setting(r.detail()!=null?r.detail():r.payload());
            List<PlanListVo.ProductSummary> goods=new ArrayList<>();
            if(r!=null) for(var node:r.payload().path("product_info")) {
                String pid=QianchuanPlanMapping.text(node,"product_id");
                if(pid!=null) goods.add(new PlanListVo.ProductSummary(pid,node.path("product_name").asText(pid),QianchuanPlanMapping.text(node,"product_image")));
            }
            if(goods.isEmpty()) {
                var pids=selected.getOrDefault(plan.id().value(),plan.productIds().stream().map(QcProductId::value).toList());
                var catalog=pids.isEmpty()?List.<Product>of():catalogs.computeIfAbsent(plan.awemeId().value(),key->products.findByAweme(tenant,plan.awemeId()));
                for(String pid:pids) goods.add(catalog.stream().filter(p->pid.equals(p.qcProductId().value())).findFirst()
                        .map(p->new PlanListVo.ProductSummary(pid,p.name(),p.imageUrl())).orElse(new PlanListVo.ProductSummary(pid,pid,null)));
            }
            boolean current=r!=null && LocalDate.now(ZoneId.of("Asia/Shanghai")).equals(r.reportDate());
            var report=current?r.todayReport():null;
            BigDecimal cost=r==null?(snap==null?null:snap.cost().amount()):metric(report,"stat_cost");
            BigDecimal gmv=r==null?(snap==null?null:snap.gmv().amount()):metric(report,"total_pay_order_gmv_include_coupon_for_roi2");
            BigDecimal roi=r==null?(snap==null || snap.roi()==null?null:snap.roi().value()):metric(report,"total_prepay_and_pay_order_roi2");
            BigDecimal orders=metric(report,"total_pay_order_count_for_roi2");
            Long convert=r==null?(snap==null?null:snap.convert()):orders==null?null:orders.longValue();
            PlanListVo.MetricComparison comparison=r==null?comparison(pair):current?comparison(report,r.yesterdayReport(),r.reportDate()):null;
            boolean canEdit=plan.qcPlanId().isPresent() && authorized.contains(plan.accountId().value()) &&
                    (plan.status()==PlanStatus.DELIVERING || plan.status()==PlanStatus.PAUSED);
            result.add(new PlanListVo(plan.id().value(),plan.name().text(),plan.status().name(),plan.source().name(),plan.qcPlanId().map(QcPlanId::value).orElse(null),
                    plan.source()==PlanSource.LINGTOU,goods.isEmpty()?null:goods.getFirst().imageUrl(),r==null?null:r.guarantee(),cost,gmv,roi,
                    plan.setting().bidType().oceanValue(),setting==null?plan.setting().dailyBudget().amount():QianchuanPlanMapping.number(setting,"budget"),
                    setting==null?plan.setting().roi2Goal().map(RoiGoal::value).orElse(null):QianchuanPlanMapping.number(setting,"roi2_goal"),
                    plan.setting().schedule().startDate(),plan.setting().schedule().endDate(),convert,
                    r==null?(snap==null?null:snap.ts()):current?r.reportTs():null,List.copyOf(goods),comparison,
                    info==null?(metadata==null?null:metadata.createdAt()):QianchuanPlanMapping.instant(QianchuanPlanMapping.text(info,"create_time")),
                    r==null?(metadata==null?null:metadata.deliveryPlatform()):"ALL",plan.accountId().value(),plan.qcStatus(),canEdit,
                    canEdit && plan.status()==PlanStatus.DELIVERING));
        }
        return result;
    }
    private static BigDecimal metric(JsonNode report,String field) { return report==null?null:QianchuanPlanMapping.number(report,field); }
    private static PlanListVo.MetricComparison comparison(JsonNode today,JsonNode yesterday,LocalDate day) {
        if(today==null || yesterday==null) return null;
        BigDecimal current=metric(today,"total_prepay_and_pay_order_roi2"),previous=metric(yesterday,"total_prepay_and_pay_order_roi2");
        return new PlanListVo.MetricComparison(change(metric(today,"stat_cost"),metric(yesterday,"stat_cost")),
                change(metric(today,"total_pay_order_count_for_roi2"),metric(yesterday,"total_pay_order_count_for_roi2")),
                current==null || previous==null?null:current.subtract(previous),day.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().minusSeconds(1),change(current,previous),"YESTERDAY_FULL_DAY");
    }
    private static PlanListVo.MetricComparison comparison(SnapshotPair pair) {
        if(pair==null || pair.current()==null || pair.previous()==null) return null;
        var c=pair.current();var p=pair.previous();
        return new PlanListVo.MetricComparison(change(c.cost().amount(),p.cost().amount()),change(BigDecimal.valueOf(c.convert()),BigDecimal.valueOf(p.convert())),
                c.roi()==null || p.roi()==null?null:c.roi().value().subtract(p.roi().value()),p.ts(),
                c.roi()==null || p.roi()==null?null:change(c.roi().value(),p.roi().value()));
    }
    private static BigDecimal change(BigDecimal c,BigDecimal p) { return c==null || p==null || p.signum()<=0?null:c.subtract(p).multiply(BigDecimal.valueOf(100)).divide(p,2,RoundingMode.HALF_UP); }
    private static boolean matches(PlanListVo row,Filters f) {
        if(f==null) return true;
        if(nonblank(f.status()) && !f.status().equals(row.status())) return false;
        if(nonblank(f.platform()) && !f.platform().equals(row.deliveryPlatform())) return false;
        if(nonblank(f.bidType()) && !f.bidType().equals(row.smartBidType())) return false;
        if(nonblank(f.accountId()) && !f.accountId().equals(row.accountId())) return false;
        if(f.createdWithin()!=null && f.createdWithin()>0) {
            LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
            if(row.createdAt()==null) return false;
            LocalDate day=row.createdAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            if(day.isBefore(today.minusDays(f.createdWithin()-1)) || day.isAfter(today)) return false;
        }
        if(!nonblank(f.keyword())) return true;
        String q=f.keyword().trim().toLowerCase(Locale.ROOT);
        return row.name().toLowerCase(Locale.ROOT).contains(q) || row.planId().contains(q) || (row.qcPlanId()!=null && row.qcPlanId().contains(q)) ||
                row.products().stream().anyMatch(p->p.name().toLowerCase(Locale.ROOT).contains(q) || p.id().contains(q));
    }
    private static boolean nonblank(String value) { return value!=null && !value.isBlank(); }
    private static void sort(List<PlanListVo> rows,Filters f) {
        if(f==null || !nonblank(f.sortField())) { rows.sort(Comparator.comparing(PlanListVo::createdAt,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(PlanListVo::planId)); return; }
        Comparator<PlanListVo> comparator;
        if("name".equals(f.sortField())) comparator=Comparator.comparing(PlanListVo::name,f.descending()?Comparator.reverseOrder():Comparator.naturalOrder());
        else {
            java.util.function.Function<PlanListVo,BigDecimal> field=switch(f.sortField()) {
                case "costYuan" -> PlanListVo::costYuan; case "gmvYuan" -> PlanListVo::gmvYuan;
                case "roi" -> PlanListVo::roi; case "convert" -> row->row.convert()==null?null:BigDecimal.valueOf(row.convert());
                default -> throw new BizException(1402,"排序字段不正确");
            };
            comparator=Comparator.comparing(field,Comparator.nullsLast(f.descending()?Comparator.<BigDecimal>reverseOrder():Comparator.<BigDecimal>naturalOrder()));
        }
        rows.sort(comparator.thenComparing(PlanListVo::planId));
    }
    private static Summary summary(List<PlanListVo> rows) {
        var covered=rows.stream().filter(r->r.costYuan()!=null && r.gmvYuan()!=null && r.convert()!=null).toList();
        if(covered.isEmpty()) return new Summary(null,null,null,null,0,rows.size(),null);
        BigDecimal cost=covered.stream().map(PlanListVo::costYuan).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal gmv=covered.stream().map(PlanListVo::gmvYuan).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Summary(cost,gmv,covered.stream().mapToLong(PlanListVo::convert).sum(),cost.signum()>0?gmv.divide(cost,4,RoundingMode.HALF_UP):null,
                covered.size(),rows.size(),covered.stream().map(PlanListVo::snapshotTs).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
    }
}
