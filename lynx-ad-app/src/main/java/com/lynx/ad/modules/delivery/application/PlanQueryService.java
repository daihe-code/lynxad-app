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
    @org.springframework.beans.factory.annotation.Autowired
    private com.lynx.ad.modules.tenant.application.WorkspaceAccess workspace;
    private final AdPlanRepository plans;
    private final MetricSnapshotRepository snapshots;
    private final ProductRepository products;
    private final AdPlanCreativeBindingStore bindings;
    private final PlanRemoteStore remote;
    private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;
    private com.lynx.ad.modules.catalog.application.ProductPriceService prices;
    @Autowired public void productPrices(com.lynx.ad.modules.catalog.application.ProductPriceService value) { prices=value; }
    private com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway catalog;
    @Autowired public void productCatalog(com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway value) { catalog=value; }
    private PlanStatisticsService statisticsService;
    @Autowired public void statisticsService(PlanStatisticsService value) { statisticsService=value; }
    private CreationStore creation;
    private com.lynx.ad.modules.creative.application.AssetUploadService assets;
    @Autowired public void previewAssets(com.lynx.ad.modules.creative.application.AssetUploadService value){assets=value;}
    private PlanReviewStore reviews;
    @Autowired public void reviewStore(PlanReviewStore value){reviews=value;}
    @Autowired public void creationStore(CreationStore store) { creation=store; }
    private PlanTransferStore transfers;
    @Autowired public void transferStore(PlanTransferStore store) { transfers=store; }
    private boolean live;
    private boolean liveAuthorization;
    @Autowired
    public void configuration(com.lynx.ad.common.config.LynxAdProperties properties) {
        live=!properties.getMock().isQianchuan();
        liveAuthorization=properties.getOauth().getQianchuan().isLive();
    }
    public PlanQueryService(AdPlanRepository plans,AdListPort list,OauthAccountRepository accounts,
                            QianchuanCredentialFacade credentials,MetricSnapshotRepository snapshots,
                            ProductRepository products,AdPlanCreativeBindingStore bindings) {
        this(plans,list,accounts,credentials,snapshots,products,bindings,null);
    }
    @Autowired
    public PlanQueryService(AdPlanRepository plans,AdListPort list,OauthAccountRepository accounts,
                            QianchuanCredentialFacade credentials,MetricSnapshotRepository snapshots,
                            ProductRepository products,AdPlanCreativeBindingStore bindings,PlanRemoteStore remote) {
        this.plans=plans;this.snapshots=snapshots;this.products=products;this.bindings=bindings;this.remote=remote;this.accounts=accounts;this.credentials=credentials;
    }
    public record Filters(String keyword,String status,String platform,String bidType,Integer createdWithin,
                          String accountId,String sortField,boolean descending,PlanStatisticsRange statisticsRange,boolean refreshStatistics,PlanMetricTemplate metricTemplate) {
        public Filters(String keyword,String status,String platform,String bidType,Integer createdWithin,String accountId,String sortField,boolean descending,PlanStatisticsRange range,boolean refresh) {
            this(keyword,status,platform,bidType,createdWithin,accountId,sortField,descending,range,refresh,PlanMetricTemplate.PAYMENT);
        }
        public Filters(String keyword,String status,String platform,String bidType,Integer createdWithin,String accountId,String sortField,boolean descending) {
            this(keyword,status,platform,bidType,createdWithin,accountId,sortField,descending,null,false);
        }
    }
    public record Summary(BigDecimal cost,BigDecimal gmv,Long orders,BigDecimal roi,int covered,int plans,Instant snapshotTs,
                          BigDecimal netGmv,BigDecimal netRoi,BigDecimal refundRate,PlanListVo.MetricComparison comparison) {
        public Summary(BigDecimal cost,BigDecimal gmv,Long orders,BigDecimal roi,int covered,int plans,Instant snapshotTs,BigDecimal netGmv,BigDecimal netRoi,BigDecimal refundRate) {this(cost,gmv,orders,roi,covered,plans,snapshotTs,netGmv,netRoi,refundRate,null);}
        public Summary(BigDecimal cost,BigDecimal gmv,Long orders,BigDecimal roi,int covered,int plans,Instant snapshotTs) {this(cost,gmv,orders,roi,covered,plans,snapshotTs,null,null,null);}
    }
    public record PageResult<T>(List<T> records,long total,int pageNum,int pageSize,Summary summary,PlanStatisticsRange statisticsRange,PlanMetricTemplate metricTemplate) {
        public PageResult(List<T> records,long total,int pageNum,int pageSize,Summary summary,PlanStatisticsRange range) {this(records,total,pageNum,pageSize,summary,range,PlanMetricTemplate.PAYMENT);}
        public PageResult(List<T> records,long total,int pageNum,int pageSize) { this(records,total,pageNum,pageSize,null); }
        public PageResult(List<T> records,long total,int pageNum,int pageSize,Summary summary) { this(records,total,pageNum,pageSize,summary,null); }
    }
    public PageResult<PlanListVo> list(TenantId tenant,int pageNum,int pageSize) { return list(tenant,pageNum,pageSize,null); }
    public Map<String,Set<String>> statisticsScope(TenantId tenant,Filters filter) {
        String status=filter==null?null:filter.status();
        com.lynx.ad.modules.delivery.domain.service.PlanStatusPolicy.validate(status);
        Map<String,Set<String>> scope=new LinkedHashMap<>();
        for(var row:rows(tenant,null,com.lynx.ad.modules.delivery.domain.service.PlanStatusPolicy.includesDeleted(status)))
            if(row.qcPlanId()!=null && matches(row,filter))scope.computeIfAbsent(row.accountId(),key->new HashSet<>()).add(row.qcPlanId());
        return scope;
    }
    public PageResult<PlanListVo> list(TenantId tenant,int pageNum,int pageSize,Filters filter) {
        if(pageNum<1 || pageSize<1 || pageSize>200) throw new BizException(1402,"分页参数不正确");
        String status=filter==null?null:filter.status();
        com.lynx.ad.modules.delivery.domain.service.PlanStatusPolicy.validate(status);
        var template=filter==null || filter.metricTemplate()==null?PlanMetricTemplate.PAYMENT:filter.metricTemplate();
        var range=filter==null?null:filter.statisticsRange();
        if(template==PlanMetricTemplate.NET && range==null) {
            LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));range=new PlanStatisticsRange(today,today);
        }
        List<PlanListVo> all=rows(tenant,null,com.lynx.ad.modules.delivery.domain.service.PlanStatusPolicy.includesDeleted(status)).stream().filter(row->matches(row,filter)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if(range!=null)all=rangeRows(tenant,all,range,filter!=null && filter.refreshStatistics(),template);
        sort(all,filter);
        long from=(long)(pageNum-1)*pageSize;
        return new PageResult<>(from>=all.size()?List.of():List.copyOf(all.subList((int)from,(int)Math.min(all.size(),from+pageSize))),
                all.size(),pageNum,pageSize,overviewSummary(all,template,range),range,template);
    }
    private List<PlanListVo> rangeRows(TenantId tenant,List<PlanListVo> rows,PlanStatisticsRange range,boolean refresh,PlanMetricTemplate template) {
        Map<String,PlanStatisticsService.Snapshot> reports=new HashMap<>();
        Map<String,PlanStatisticsService.Snapshot> previousReports=new HashMap<>();
        Set<String> comparisonFailed=new HashSet<>();
        long days=java.time.temporal.ChronoUnit.DAYS.between(range.startDate(),range.endDate())+1;
        var previousRange=new PlanStatisticsRange(range.startDate().minusDays(days),range.endDate().minusDays(days));
        for(var row:rows) if(row.qcPlanId()!=null && !reports.containsKey(row.accountId())) {
            reports.put(row.accountId(),template==PlanMetricTemplate.PAYMENT?statisticsService.report(tenant,row.accountId(),range,refresh):statisticsService.report(tenant,row.accountId(),range,refresh,template));
            try { previousReports.put(row.accountId(),template==PlanMetricTemplate.PAYMENT?statisticsService.report(tenant,row.accountId(),previousRange,refresh):statisticsService.report(tenant,row.accountId(),previousRange,refresh,template)); }
            catch(BizException unavailable) { comparisonFailed.add(row.accountId()); }
        }
        List<PlanListVo> result=new ArrayList<>();
        for(var row:rows) {
            var report=reports.get(row.accountId());
            var m=report==null || row.qcPlanId()==null?null:report.forPlan(row.qcPlanId());
            var previousReport=previousReports.get(row.accountId());
            var previous=previousReport==null || row.qcPlanId()==null?null:previousReport.forPlan(row.qcPlanId());
            var comparison=row.qcPlanId()==null?null:rangeComparison(m,previous,range,previousRange,comparisonFailed.contains(row.accountId()));
            result.add(new PlanListVo(row.planId(),row.name(),row.status(),row.source(),row.qcPlanId(),row.lingtou(),row.coverUrl(),row.guarantee(),
                    m==null?null:m.cost(),m==null?null:m.gmv(),m==null?null:m.roi(),row.smartBidType(),row.budget(),row.roi2Goal(),row.startDate(),row.endDate(),
                    m==null?null:m.orders(),report==null?null:report.fetchedAt(),row.products(),comparison,row.createdAt(),row.deliveryPlatform(),row.accountId(),row.qcStatus(),
                    row.canEdit(),row.canControl(),row.roiEstimate(),row.canTransfer(),null,false,row.review(),row.awemeId(),row.materialCoverUrl(),
                    m==null?null:m.netGmv(),m==null?null:m.netRoi(),m==null?null:m.refundRate(),row.awemeName(),row.awemeAvatarUrl()));
        }
        return result;
    }
    private static PlanListVo.MetricComparison rangeComparison(PlanStatisticsService.Metrics current,PlanStatisticsService.Metrics previous,
                                                               PlanStatisticsRange range,PlanStatisticsRange previousRange,boolean failed) {
        String basis=range.startDate().equals(range.endDate())
                ?range.endDate().equals(LocalDate.now(ZoneId.of("Asia/Shanghai")))?"YESTERDAY_FULL_DAY":"PREVIOUS_DAY":"PREVIOUS_PERIOD";
        String reason=failed?"对比报表获取失败，请刷新重试":current==null?"当前区间暂无报表":previous==null?"对比时段暂无报表":null;
        return new PlanListVo.MetricComparison(
                current==null || previous==null?null:rangeChange(current.cost(),previous.cost()),
                current==null || previous==null?null:rangeChange(decimal(current.orders()),decimal(previous.orders())),
                current==null || previous==null || current.roi()==null || previous.roi()==null?null:current.roi().subtract(previous.roi()),
                previousRange.endDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().minusSeconds(1),
                current==null || previous==null?null:rangeChange(current.roi(),previous.roi()),basis,
                current==null || previous==null?null:rangeChange(current.gmv(),previous.gmv()),
                metricValues(current),metricValues(previous),previousRange.startDate(),previousRange.endDate(),reason);
    }
    private static PlanListVo.MetricValues metricValues(PlanStatisticsService.Metrics m) {
        return m==null?null:new PlanListVo.MetricValues(m.cost(),m.gmv(),m.roi(),m.orders(),m.netGmv(),m.netRoi(),m.refundRate());
    }
    private static BigDecimal decimal(Long value) { return value==null?null:BigDecimal.valueOf(value); }
    private static BigDecimal rangeChange(BigDecimal current,BigDecimal previous) {
        if(current==null || previous==null || current.signum()<0 || previous.signum()<0)return null;
        if(previous.signum()==0)return current.signum()==0?BigDecimal.ZERO:null;
        return change(current,previous);
    }
    private com.lynx.ad.modules.delivery.infrastructure.persistence.PlanSettingsEditStore settingsEdits;
    private com.lynx.ad.modules.delivery.infrastructure.persistence.DraftExpiryStore draftExpiry;
    @Autowired public void draftExpiry(com.lynx.ad.modules.delivery.infrastructure.persistence.DraftExpiryStore value){draftExpiry=value;}
    @org.springframework.beans.factory.annotation.Autowired public void settingsEdits(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanSettingsEditStore value){settingsEdits=value;}

    public PlanVo get(TenantId tenant,PlanId id) {
        if(draftExpiry!=null)draftExpiry.assertAvailable(tenant.value(),id.value(),Instant.now());
        var plan=plans.findById(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在"));
        var visible=rows(tenant,plan);
        if(visible.isEmpty()) throw new BizException(1405,"计划不存在或不属于真实千川授权账户");
        var row=visible.getFirst();
        var r=remote==null?null:remote.list(tenant).get(id.value());
        String aweme=row.awemeId();
        String detailAweme=r==null?null:QianchuanPlanMapping.awemeId(r.detail());
        if(detailAweme!=null)aweme=detailAweme;
        var goods=row.products();
        if(live && aweme.matches("[1-9][0-9]{0,18}") && !goods.isEmpty()) {
            // Imported history has no local creation snapshot. Read existing products without
            // applying new-plan eligibility checks, or dropping products no longer in the catalog.
            if(catalog!=null)try {
                var current=new HashMap<String,CreationState.Product>();
                for(int offset=0;offset<goods.size();offset+=10) {
                    var ids=goods.subList(offset,Math.min(offset+10,goods.size())).stream().map(PlanListVo.ProductSummary::id).distinct().toList();
                    for(var product:catalog.restorable(tenant,plan.accountId().value(),aweme,ids))current.put(product.qcProductId(),product);
                }
                goods=goods.stream().map(p->{var product=current.get(p.id());return product==null?p:new PlanListVo.ProductSummary(p.id(),p.name(),p.imageUrl(),product.priceInfo()==null?p.priceInfo():product.priceInfo(),product.stock(),product.sales());}).toList();
            }catch(BizException unavailable){/* Historical products stay visible even if unavailable now. */}
            if(prices!=null)try {
            var c=credentials.getUsableCredential(tenant,plan.accountId());
            var refs=goods.stream().map(p->new com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.Ref(p.id(),null)).toList();
            var quotes=prices.prices(tenant,c,aweme,refs,false);
            goods=goods.stream().map(p->new PlanListVo.ProductSummary(p.id(),p.name(),p.imageUrl(),quotes.getOrDefault(p.id(),p.priceInfo()),p.stock(),p.sales())).toList();
            }catch(BizException unavailable){/* Saved plan details remain readable. */}
        }
        return new PlanVo(row.planId(),row.name(),row.status(),row.source(),row.qcPlanId(),row.smartBidType(),row.budget(),aweme,
                plan.rejectReason(),row.lingtou(),row.guarantee(),row.coverUrl(),row.costYuan(),row.gmvYuan(),row.roi(),
                plan.accountId().value(),row.roi2Goal(),row.startDate(),row.endDate(),row.convert(),row.snapshotTs(),goods,row.comparison(),
                row.createdAt(),row.deliveryPlatform(),plan.qcStatus(),r==null?null:r.detail(),row.statisticsDate(),row.historicalMetrics(),settingsEdits==null?null:settingsEdits.state(plan),row.review());
    }
    private List<PlanListVo> rows(TenantId tenant,AdPlan only) {
        return rows(tenant,only,false);
    }
    private List<PlanListVo> rows(TenantId tenant,AdPlan only,boolean includeDeleted) {
        if(draftExpiry!=null)draftExpiry.expireDue(tenant.value(),Instant.now(),200);
        var meta=plans.listMetadataByTenant(tenant);
        var now=Instant.now();
        var local=(only==null?plans.findByTenant(tenant):List.of(only)).stream()
                .filter(p -> includeDeleted || p.status()!=PlanStatus.DELETED)
                .filter(p -> p.status()!=PlanStatus.DRAFT || p.qcPlanId().isPresent() || p.source()!=PlanSource.LINGTOU
                        || !com.lynx.ad.modules.delivery.domain.service.DraftExpiryPolicy.expired(meta.containsKey(p.id().value())?meta.get(p.id().value()).createdAt():null,now)).toList();
        var groups=transfers==null?Map.<String,PlanTransferStore.Group>of():transfers.groups(tenant);
        var latest=snapshots.listLatestPairsByTenant(tenant);
        var selected=bindings.productIdsByPlan(local.stream().map(p->p.id().value()).toList());
        var creationSummaries=creation==null?Map.<String,CreationStore.PlanSummary>of():creation.summariesByPlan(tenant.value(),local.stream().map(p->groups.containsKey(p.id().value())?groups.get(p.id().value()).root():p.id().value()).distinct().toList());
        var coverAssets=creationSummaries.values().stream().map(CreationStore.PlanSummary::materialCoverAssetId).filter(Objects::nonNull).distinct().toList();
        var covers=assets==null||coverAssets.isEmpty()?Map.<String,String>of():assets.previewImages(tenant,coverAssets);
        var cached=remote==null?Map.<String,PlanRemoteStore.Remote>of():remote.list(tenant);
        var reviewStates=reviews==null?Map.<String,com.lynx.ad.modules.delivery.domain.service.PlanReviewProgress.View>of():reviews.list(tenant);
        Map<String,List<Product>> catalogs=new HashMap<>();
        Set<String> authorized=new HashSet<>();
        Set<String> realAccounts=new HashSet<>();
        var allowed=workspace==null?java.util.Optional.<Set<String>>empty():workspace.readableBindings(tenant.value());
        for(var account:accounts.findByTenant(tenant)) {
            if(allowed.isPresent()&&!allowed.get().contains(account.id().value()))continue;
            if(account.platform()!=com.lynx.ad.modules.identity.domain.model.Platform.QIANCHUAN) continue;
            if(!liveAuthorization || account.grantId()!=null) realAccounts.add(account.id().value());
            if(realAccounts.contains(account.id().value()) && "ACTIVE".equals(account.status()) &&
                (account.refreshExpiresAt()==null || account.refreshExpiresAt().isAfter(Instant.now()))) authorized.add(account.id().value());
        }
        List<PlanListVo> result=new ArrayList<>();
        for(var plan:local) {
            var group=groups.get(plan.id().value());String logicalId=group==null?plan.id().value():group.root();
            if(liveAuthorization && !realAccounts.contains(plan.accountId().value())) continue;
            var metadata=meta.get(plan.id().value()); var pair=latest.get(plan.id().value()); var snap=pair==null?null:pair.current();
            var r=cached.get(plan.id().value()); JsonNode info=r==null?null:QianchuanPlanMapping.info(r.payload());
            String aweme=plan.awemeId().value();
            if(r!=null && "unknown".equals(aweme)) {
                String confirmed=QianchuanPlanMapping.awemeId(r.detail());
                if(confirmed==null)confirmed=QianchuanPlanMapping.awemeId(r.payload());
                if(confirmed!=null)aweme=confirmed;
            }
            String awemeName=r==null?null:QianchuanPlanMapping.anchorText(r.payload(),"anchor_name");
            String awemeAvatar=r==null?null:QianchuanPlanMapping.anchorText(r.payload(),"anchor_avatar");
            var creationSummary=creationSummaries.get(logicalId);
            if(live && r==null) snap=null;
            JsonNode setting=r==null?null:QianchuanPlanMapping.setting(r.detail()!=null?r.detail():r.payload());
            var schedule=plan.setting().schedule();
            if(r!=null)schedule=com.lynx.ad.modules.delivery.domain.service.PlanSchedule.read(QianchuanPlanMapping.setting(r.payload()),schedule);
            if(r!=null && r.detail()!=null)schedule=com.lynx.ad.modules.delivery.domain.service.PlanSchedule.read(setting,schedule);
            List<PlanListVo.ProductSummary> goods=new ArrayList<>();
            if(group!=null && creationSummary!=null)for(var product:creationSummary.products())
                goods.add(new PlanListVo.ProductSummary(product.qcProductId(),product.name(),product.imageUrl()));
            if(goods.isEmpty() && r!=null) for(var node:r.payload().path("product_info")) {
                String pid=QianchuanPlanMapping.text(node,"product_id");
                if(pid!=null) goods.add(new PlanListVo.ProductSummary(pid,node.path("product_name").asText(pid),QianchuanPlanMapping.text(node,"product_image")));
            }
            if(goods.isEmpty()) {
                for(var product:creationSummary==null?List.<CreationState.Product>of():creationSummary.products())
                    goods.add(new PlanListVo.ProductSummary(product.qcProductId(),product.name(),product.imageUrl()));
            }
            if(goods.isEmpty()) {
                var pids=selected.getOrDefault(plan.id().value(),plan.productIds().stream().map(QcProductId::value).toList());
                var catalog=pids.isEmpty()?List.<Product>of():catalogs.computeIfAbsent(plan.awemeId().value(),key->products.findByAweme(tenant,plan.awemeId()));
                for(String pid:pids) goods.add(catalog.stream().filter(p->pid.equals(p.qcProductId().value())).findFirst()
                        .map(p->new PlanListVo.ProductSummary(pid,p.name(),p.imageUrl())).orElse(new PlanListVo.ProductSummary(pid,pid,null)));
            }
            LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
            var statistics=statistics(r,today);
            boolean current=statistics!=null && today.equals(statistics.day()) && today.equals(r.reportDate());
            var report=statistics==null?null:statistics.data();
            BigDecimal cost=r==null?(snap==null?null:snap.cost().amount()):metric(report,"stat_cost");
            BigDecimal gmv=r==null?(snap==null?null:snap.gmv().amount()):metric(report,"total_pay_order_gmv_include_coupon_for_roi2");
            BigDecimal roi=r==null?(snap==null || snap.roi()==null?null:snap.roi().value()):metric(report,"total_prepay_and_pay_order_roi2");
            BigDecimal orders=metric(report,"total_pay_order_count_for_roi2");
            Long convert=r==null?(snap==null?null:snap.convert()):orders==null?null:orders.longValue();
            PlanListVo.MetricComparison comparison=r==null?comparison(pair):current?comparison(report,r.yesterdayReport(),r.reportDate()):null;
            boolean canEdit=plan.qcPlanId().isPresent() && authorized.contains(plan.accountId().value()) &&
                    (plan.status()==PlanStatus.DELIVERING || plan.status()==PlanStatus.PAUSED);
            BigDecimal budget=setting==null?plan.setting().dailyBudget().amount():QianchuanPlanMapping.number(setting,"budget");
            String materialCover=com.lynx.ad.modules.delivery.domain.service.PlanMaterialCover.remote(r==null?null:r.detail());
            if(materialCover==null)materialCover=com.lynx.ad.modules.delivery.domain.service.PlanMaterialCover.remote(r==null?null:r.payload());
            if(materialCover==null && creationSummary!=null && creationSummary.materialCoverAssetId()!=null)materialCover=covers.get(creationSummary.materialCoverAssetId());
            result.add(new PlanListVo(plan.id().value(),plan.name().text(),plan.status().name(),plan.source().name(),plan.qcPlanId().map(QcPlanId::value).orElse(null),
                    plan.source()==PlanSource.LINGTOU,goods.isEmpty()?null:goods.getFirst().imageUrl(),r==null?null:r.guarantee(),cost,gmv,roi,
                    plan.setting().bidType().oceanValue(),budget,
                    setting==null?plan.setting().roi2Goal().map(RoiGoal::value).orElse(null):QianchuanPlanMapping.number(setting,"roi2_goal"),
                    schedule.startDate(),schedule.type()==ScheduleType.FROM_NOW?null:schedule.endDate(),convert,
                    r==null?(snap==null?null:snap.ts()):statistics==null?null:statistics.ts(),List.copyOf(goods),comparison,
                    info==null?(metadata==null?null:metadata.createdAt()):QianchuanPlanMapping.instant(QianchuanPlanMapping.text(info,"create_time")),
                    r==null?(metadata==null?null:metadata.deliveryPlatform()):"ALL",plan.accountId().value(),plan.qcStatus(),canEdit,
                    canEdit && plan.status()==PlanStatus.DELIVERING,
                    plan.setting().bidType()==SmartBidType.CONSERVATIVE ? roiEstimate(setting,creationSummary,budget) : null,
                    canEdit,statistics==null?null:statistics.day(),statistics!=null && !today.equals(statistics.day()),plan.status()==PlanStatus.PENDING_AUDIT?reviewStates.get(plan.id().value()):null,
                    aweme,materialCover,null,null,null,awemeName,awemeAvatar));
        }
        return result;
    }
    private record Statistics(JsonNode data,LocalDate day,Instant ts) {}
    private static Statistics statistics(PlanRemoteStore.Remote remote,LocalDate today) {
        if(remote==null)return null;
        List<Statistics> candidates=new ArrayList<>();
        if(remote.reportDate()!=null) {
            candidates.add(new Statistics(remote.todayReport(),remote.reportDate(),remote.reportTs()));
            candidates.add(new Statistics(remote.yesterdayReport(),remote.reportDate().minusDays(1),remote.reportTs()));
        }
        candidates.add(new Statistics(remote.lastReport(),remote.lastReportDate(),remote.lastReportTs()));
        return candidates.stream().filter(s->s.day()!=null && !s.day().isAfter(today) && PlanRemoteStore.validReport(s.data()))
                .max(Comparator.comparing(Statistics::day).thenComparing(Statistics::ts,Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
    }
    private static PlanListVo.RoiEstimate roiEstimate(JsonNode setting,CreationStore.PlanSummary summary,BigDecimal budget) {
        if(setting!=null) {
            BigDecimal value=nonNegative(setting,"estimate_roi_goal"),min=nonNegative(setting,"min_estimate_roi_goal"),max=nonNegative(setting,"max_estimate_roi_goal");
            if(value!=null || min!=null || max!=null)return new PlanListVo.RoiEstimate(value,min,max);
        }
        if(summary==null || summary.recommendations()==null || budget==null)return null;
        for(var estimate:summary.recommendations().path("budget").path("estimate_trans_cost_list")) {
            BigDecimal tier=QianchuanPlanMapping.number(estimate,"budget");
            if(tier==null || tier.compareTo(budget)!=0)continue;
            BigDecimal value=nonNegative(estimate,"estimate_roi"),min=nonNegative(estimate,"min_estimate_roi"),max=nonNegative(estimate,"max_estimate_roi");
            return value==null && min==null && max==null ? null : new PlanListVo.RoiEstimate(value,min,max);
        }
        return null;
    }
    private static BigDecimal nonNegative(JsonNode node,String key) {
        BigDecimal value=QianchuanPlanMapping.number(node,key);
        return value!=null && value.signum()>=0 ? value : null;
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
        if(!com.lynx.ad.modules.delivery.domain.service.PlanStatusPolicy.matches(row.status(),row.qcStatus(),f==null?null:f.status()))return false;
        if(f==null) return true;
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
                case "netGmvYuan" -> PlanListVo::netGmvYuan;case "netRoi" -> PlanListVo::netRoi;case "refundRate" -> PlanListVo::refundRate;
                default -> throw new BizException(1402,"排序字段不正确");
            };
            comparator=Comparator.comparing(field,Comparator.nullsLast(f.descending()?Comparator.<BigDecimal>reverseOrder():Comparator.<BigDecimal>naturalOrder()));
        }
        rows.sort(comparator.thenComparing(PlanListVo::planId));
    }
    private static Summary overviewSummary(List<PlanListVo> rows,PlanMetricTemplate template,PlanStatisticsRange range) {
        var current=summary(rows,template);
        if(range==null)return current;
        Map<String,PlanStatisticsService.Metrics> previous=new LinkedHashMap<>();Set<String> currentIds=new HashSet<>();
        boolean failed=false;
        for(var row:rows) {
            if(row.costYuan()!=null && (template==PlanMetricTemplate.NET || row.gmvYuan()!=null && row.convert()!=null))currentIds.add(row.planId());
            var comparison=row.comparison();if(comparison==null)continue;
            if("对比报表获取失败，请刷新重试".equals(comparison.unavailableReason()))failed=true;
            var value=comparison.previous();
            if(value!=null && value.cost()!=null && (template==PlanMetricTemplate.NET || value.gmv()!=null && value.orders()!=null))
                previous.put(row.planId(),new PlanStatisticsService.Metrics(value.cost(),value.gmv(),value.roi(),value.orders(),value.netGmv(),value.netRoi(),value.refundRate()));
        }
        // Compare the complete filtered cohort, never one page or two differently covered subsets.
        PlanStatisticsService.Metrics prior=null;
        if(!failed && !previous.isEmpty() && currentIds.equals(previous.keySet())) {
            var totals=CampaignTrendService.aggregate(List.copyOf(previous.values()));
            prior=new PlanStatisticsService.Metrics(totals.get("cost"),totals.get("gmv"),totals.get("roi"),totals.get("orders")==null?null:totals.get("orders").longValue(),totals.get("netGmv"),totals.get("netRoi"),totals.get("refundRate"));
        }
        long days=java.time.temporal.ChronoUnit.DAYS.between(range.startDate(),range.endDate())+1;
        var previousRange=new PlanStatisticsRange(range.startDate().minusDays(days),range.endDate().minusDays(days));
        var values=current.covered()==0?null:new PlanStatisticsService.Metrics(current.cost(),current.gmv(),current.roi(),current.orders(),current.netGmv(),current.netRoi(),current.refundRate());
        var comparison=rangeComparison(values,prior,range,previousRange,failed);
        return new Summary(current.cost(),current.gmv(),current.orders(),current.roi(),current.covered(),current.plans(),current.snapshotTs(),current.netGmv(),current.netRoi(),current.refundRate(),comparison);
    }
    private static Summary summary(List<PlanListVo> rows,PlanMetricTemplate template) {
        if(template==PlanMetricTemplate.NET)return netSummary(rows);
        var covered=rows.stream().filter(r->r.costYuan()!=null && r.gmvYuan()!=null && r.convert()!=null).toList();
        if(covered.isEmpty()) return new Summary(null,null,null,null,0,rows.size(),null);
        BigDecimal cost=covered.stream().map(PlanListVo::costYuan).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal gmv=covered.stream().map(PlanListVo::gmvYuan).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Summary(cost,gmv,covered.stream().mapToLong(PlanListVo::convert).sum(),cost.signum()>0?gmv.divide(cost,4,RoundingMode.HALF_UP):null,
                covered.size(),rows.size(),covered.stream().map(PlanListVo::snapshotTs).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
    }
    private static Summary netSummary(List<PlanListVo> rows) {
        var covered=rows.stream().filter(r->r.costYuan()!=null).toList();
        if(covered.isEmpty())return new Summary(null,null,null,null,0,rows.size(),null);
        BigDecimal cost=covered.stream().map(PlanListVo::costYuan).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal gmv=covered.stream().allMatch(r->r.gmvYuan()!=null)?covered.stream().map(PlanListVo::gmvYuan).reduce(BigDecimal.ZERO,BigDecimal::add):null;
        BigDecimal netGmv=covered.stream().allMatch(r->r.netGmvYuan()!=null)?covered.stream().map(PlanListVo::netGmvYuan).reduce(BigDecimal.ZERO,BigDecimal::add):null;
        var paid=covered.stream().filter(r->r.gmvYuan()!=null && r.gmvYuan().signum()>0).toList();
        // Weight monetary refund rates by transaction amount, never by plan count or spend.
        BigDecimal refundRate=gmv!=null && gmv.signum()>0 && paid.stream().allMatch(r->r.refundRate()!=null)
                ?paid.stream().map(r->r.refundRate().multiply(r.gmvYuan())).reduce(BigDecimal.ZERO,BigDecimal::add).divide(gmv,4,RoundingMode.HALF_UP):null;
        Long orders=covered.stream().allMatch(r->r.convert()!=null)?covered.stream().mapToLong(PlanListVo::convert).sum():null;
        return new Summary(cost,gmv,orders,gmv!=null && cost.signum()>0?gmv.divide(cost,4,RoundingMode.HALF_UP):null,
                covered.size(),rows.size(),covered.stream().map(PlanListVo::snapshotTs).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                netGmv,netGmv!=null && cost.signum()>0?netGmv.divide(cost,4,RoundingMode.HALF_UP):null,refundRate);
    }
}
