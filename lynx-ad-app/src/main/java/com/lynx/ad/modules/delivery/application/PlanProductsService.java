package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.audit.application.*;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlanProductsService {
    public record Product(String id,String name,String imageUrl,String auditStatus,Map<String,BigDecimal> metrics,boolean deletable,
            com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice priceInfo) {}
    private com.lynx.ad.modules.catalog.application.ProductPriceService prices;
    @org.springframework.beans.factory.annotation.Autowired
    public void productPrices(com.lynx.ad.modules.catalog.application.ProductPriceService value) { prices=value; }
    public record Result(List<Product> records,Map<String,BigDecimal> summary,int dataProductCount,boolean shared,Instant fetchedAt,String warning,ChangeResult pending) {}
    public record DeleteRequest(String requestKey,String expectedQcPlanId) {}
    public record ChangeResult(String requestKey,String productId,String status,String message) {}
    public record Member(String id,String qcId) {}
    private record Access(AdPlan plan,Credential credential) {}
    private static final Map<String,String> FIELDS;
    static {
        var fields=new LinkedHashMap<String,String>();
        fields.put("shows","product_show_count_for_roi2");fields.put("clicks","product_click_count_for_roi2");
        fields.put("ctr","product_cvr_rate_for_roi2");fields.put("cvr","product_convert_rate_for_roi2");
        fields.put("cost","stat_cost_for_roi2");fields.put("orders","total_pay_order_count_for_roi2");
        fields.put("gmv","total_pay_order_gmv_include_coupon_for_roi2");fields.put("roi","total_prepay_and_pay_order_roi2");FIELDS=Collections.unmodifiableMap(fields);
    }
    private final AdPlanRepository plans;
    private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;
    private final AdManagementPort port;
    private final CreationStore creations;
    private final AdPlanCreativeBindingStore bindings;
    private final PlanTransferStore transfers;
    private final PlanProductChangeStore changes;
    private final PlanRemoteStore remote;
    private final BillingTrialService billing;
    private final AuditRecorder audit;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public PlanProductsService(AdPlanRepository plans,OauthAccountRepository accounts,QianchuanCredentialFacade credentials,AdManagementPort port,
            CreationStore creations,AdPlanCreativeBindingStore bindings,PlanTransferStore transfers,PlanProductChangeStore changes,
            PlanRemoteStore remote,BillingTrialService billing,AuditRecorder audit,ObjectMapper json,PlatformTransactionManager manager) {
        this.plans=plans;this.accounts=accounts;this.credentials=credentials;this.port=port;this.creations=creations;this.bindings=bindings;
        this.transfers=transfers;this.changes=changes;this.remote=remote;this.billing=billing;this.audit=audit;this.json=json;tx=new TransactionTemplate(manager);
    }
    private Access access(TenantId tenant,PlanId id) {
        var p=plans.findById(tenant,id).filter(v->v.status()!=PlanStatus.DELETED).orElseThrow(()->new BizException(1405,"计划不存在"));
        if(p.qcPlanId().isEmpty())throw new BizException(1403,"草稿尚未发布，请在计划修改页面管理商品");
        var a=accounts.findById(tenant,p.accountId()).orElseThrow(()->new BizException(1201,"授权账户不存在"));
        if(a.platform()!=Platform.QIANCHUAN || !"ACTIVE".equals(a.status()))throw new BizException(1201,"广告账户授权已失效，请重新授权");
        return new Access(p,credentials.getUsableCredential(tenant,p.accountId()));
    }
    static Map<String,JsonNode> boundProducts(JsonNode detail,String qcId) {
        if(!qcId.equals(detail.path("ad_id").asText()))throw new BizException(1406,"千川商品信息的计划ID不匹配，请同步核查");
        if(!detail.path("multi_product_creative_list").isArray() && !detail.path("product_infos").isArray() && !detail.path("product_ids").isArray())
            throw new BizException(1406,"千川未返回完整商品绑定，无法确认商品范围");
        Map<String,JsonNode> result=new LinkedHashMap<>();
        // Creative membership is authoritative; supplemental metadata can contain historical goods.
        if(detail.path("multi_product_creative_list").isArray())for(var v:detail.path("multi_product_creative_list")) {if(v.hasNonNull("product_id"))result.put(v.path("product_id").asText(),v);}
        else if(detail.path("product_ids").isArray())for(var v:detail.path("product_ids"))result.put(v.asText(),v);
        else for(var v:detail.path("product_infos"))if(v.hasNonNull("product_id"))result.put(v.path("product_id").asText(),v);
        for(var v:detail.path("product_infos"))if(result.containsKey(v.path("product_id").asText()))result.put(v.path("product_id").asText(),v);
        for(var id:result.keySet())if(!id.matches("[1-9][0-9]*"))throw new BizException(1406,"千川返回了不正确的商品ID");
        return result;
    }
    private JsonNode detail(Access a) { return port.detail(a.credential(),a.plan().qcPlanId().orElseThrow().value()); }
    private Optional<CreationState> creation(TenantId tenant,String root) {
        try {return Optional.of(creations.get(tenant.value(),root));}catch(BizException ex){if(ex.code()==1403)return Optional.empty();throw ex;}
    }
    public Result products(TenantId tenant,PlanId id,PlanStatisticsRange range) {
        return products(tenant,id,range,false);
    }
    public Result products(TenantId tenant,PlanId id,PlanStatisticsRange range,boolean refreshPrices) {
        var a=access(tenant,id);String qcId=a.plan().qcPlanId().orElseThrow().value();
        var group=transfers.group(tenant,id);String root=group==null?id.value():group.root();
        var bound=boundProducts(detail(a),qcId);var snapshots=new HashMap<String,CreationState.Product>();
        var creation=creation(tenant,root);
        creation.ifPresent(s->s.products.forEach(p->snapshots.put(p.qcProductId(),p)));
        boolean selectionLocked=creation.map(s->selectionLocked(tenant,s)).orElse(false);
        var refs=bound.entrySet().stream().map(e->new com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.Ref(e.getKey(),
                e.getValue().path("channel_id").asText(snapshots.containsKey(e.getKey())?snapshots.get(e.getKey()).channelId():null))).toList();
        var quotes=prices==null?Map.<String,com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice>of():prices.prices(tenant,a.credential(),a.plan().awemeId().value(),refs,refreshPrices);
        var rows=new HashMap<String,JsonNode>();String warning=null;
        try {
            for(var row:port.products(a.credential(),qcId,range.startDate(),range.endDate())) {
                String product=row.path("product_id").asText(row.path("product_info").path("product_id").asText());
                if(row.path("product_info").hasNonNull("product_id") && !product.equals(row.path("product_info").path("product_id").asText()))throw new BizException(1406,"千川商品报表的商品ID不一致");
                if(row.path("is_del").asBoolean(false) || !bound.containsKey(product))continue;
                if(rows.putIfAbsent(product,row)!=null)throw new BizException(1406,"千川商品报表含重复商品，请重试");
            }
        } catch(BizException ex) { rows.clear();warning="商品统计暂不可用："+ex.getMessage(); }
        var pending=group==null || group.requestKey()==null?null:changes.get(tenant,group.requestKey()).filter(c->"PENDING".equals(c.status())).map(this::view).orElse(null);
        if(selectionLocked)warning=(warning==null?"":warning+"；")+"素材正在生成或处理中，暂不能调整商品，请完成后再试";
        List<Product> result=new ArrayList<>();Map<String,BigDecimal> sum=new LinkedHashMap<>();int count=0;
        for(var entry:bound.entrySet()) {
            String product=entry.getKey();var snapshot=snapshots.get(product);var nativeRow=rows.get(product);
            var info=nativeRow==null?entry.getValue():nativeRow.path("product_info");
            Map<String,BigDecimal> metrics=new LinkedHashMap<>();
            for(var f:FIELDS.entrySet())metrics.put(f.getKey(),nativeRow==null?null:QianchuanPlanMapping.number(nativeRow.path("stats_info"),f.getValue()));
            if(metrics.values().stream().anyMatch(Objects::nonNull))count++;
            for(String f:List.of("shows","clicks","cost","orders","gmv"))if(metrics.get(f)!=null)sum.merge(f,metrics.get(f),BigDecimal::add);
            String name=info.path("product_name").asText(snapshot==null?"商品 "+product:snapshot.name());
            String image=info.path("product_image").asText(snapshot==null?null:snapshot.imageUrl());
            var quote=quotes.get(product);
            if(quote==null && snapshot!=null && snapshot.priceInfo()!=null)quote=snapshot.priceInfo().staleCopy();
            result.add(new Product(product,name,image,info.path("audit_status").asText(null),Collections.unmodifiableMap(metrics),!selectionLocked && bound.size()>1 && (group==null || group.requestKey()==null),quote));
        }
        sum.put("ctr",ratio(sum.get("clicks"),sum.get("shows"),100));sum.put("cvr",ratio(sum.get("orders"),sum.get("clicks"),100));sum.put("roi",ratio(sum.get("gmv"),sum.get("cost"),1));
        return new Result(List.copyOf(result),Collections.unmodifiableMap(sum),count,group!=null && group.conservative()!=null && group.custom()!=null,Instant.now(),warning,pending);
    }
    private BigDecimal ratio(BigDecimal numerator,BigDecimal denominator,int scale) {
        return numerator==null || denominator==null || denominator.signum()==0?null:numerator.multiply(BigDecimal.valueOf(scale)).divide(denominator,4,java.math.RoundingMode.HALF_UP);
    }
    private List<Member> members(PlanProductChangeStore.Change c) {
        try {return Arrays.asList(json.readValue(c.members(),Member[].class));}catch(Exception ex){throw new IllegalStateException("Invalid durable product change",ex);}
    }
    private ChangeResult view(PlanProductChangeStore.Change c) { return new ChangeResult(c.key(),c.product(),c.status(),"DONE".equals(c.status())?"商品已从关联计划中删除":c.error()==null?"删除正在处理，请稍后核查":c.error()); }
    private void idleCreation(TenantId tenant,String root) {
        creation(tenant,root).ifPresent(s->{
            if(selectionLocked(tenant,s) || Set.of("QUEUED","RUNNING").contains(s.analysisStatus)
                    || Set.of("QUEUED","SENDING").contains(s.publishStatus))
                throw new BizException(1403,"素材任务仍在处理，请完成后再删除商品");
        });
    }
    private boolean selectionLocked(TenantId tenant,CreationState source) {
        if(com.lynx.ad.modules.delivery.domain.service.CreationProductSelection.locked(source))return true;
        return source.publishedEditDraftId!=null && creation(tenant,source.publishedEditDraftId)
                .map(com.lynx.ad.modules.delivery.domain.service.CreationProductSelection::locked).orElse(false);
    }
    private Access member(TenantId tenant,Member m,AdPlan source) {
        var a=access(tenant,new PlanId(m.id()));
        if(!a.plan().accountId().equals(source.accountId()) || !a.plan().awemeId().equals(source.awemeId()) || !m.qcId().equals(a.plan().qcPlanId().orElseThrow().value()))
            throw new BizException(1404,"关联计划的账户或平台ID已变化，请核查");
        return a;
    }
    private void preflight(TenantId tenant,List<Member> members,AdPlan source,String product,boolean initial) {
        boolean selectedPresent=false;
        for(var m:members) {
            var bound=boundProducts(detail(member(tenant,m,source)),m.qcId());
            if(bound.containsKey(product)) {
                if(bound.size()<=1)throw new BizException(1403,"计划至少需要保留一件商品，不能删除最后一件商品");
                if(m.id().equals(source.id().value()))selectedPresent=true;
            }
        }
        if(initial && !selectedPresent)throw new BizException(1404,"商品已不在当前计划中，请刷新商品列表");
    }
    public ChangeResult delete(TenantId tenant,PlanId id,String product,DeleteRequest request) {
        billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
        if(request==null || request.requestKey()==null || !request.requestKey().matches("[0-9a-fA-F-]{36}") || product==null || !product.matches("[1-9][0-9]*"))throw new BizException(1402,"删除请求参数不正确");
        var a=access(tenant,id);var source=a.plan();
        if(!source.qcPlanId().orElseThrow().value().equals(request.expectedQcPlanId()))throw new BizException(1404,"平台计划ID已变化，请刷新后重试");
        var c=tx.execute(s->{
            var old=changes.get(tenant,request.requestKey());
            if(old.isPresent()) {
                var found=old.get();if(!product.equals(found.product()) || members(found).stream().noneMatch(m->m.id().equals(id.value())))throw new BizException(1404,"删除请求号已用于其他商品或计划");return found;
            }
            var g=transfers.lockForProductChange(source,request.requestKey());
            // Another identical request may have committed while this request waited for the group lock.
            var concurrent=changes.get(tenant,request.requestKey());
            if(concurrent.isPresent()) {
                var found=concurrent.get();if(!product.equals(found.product()) || members(found).stream().noneMatch(m->m.id().equals(id.value())))throw new BizException(1404,"删除请求号已用于其他商品或计划");
                if("DONE".equals(found.status()))transfers.finishProductChange(tenant,g.root(),request.requestKey());return found;
            }
            idleCreation(tenant,g.root());
            var list=new ArrayList<Member>();
            for(String memberId:new LinkedHashSet<>(Arrays.asList(g.conservative(),g.custom())))if(memberId!=null) {
                var plan=access(tenant,new PlanId(memberId)).plan();list.add(new Member(memberId,plan.qcPlanId().orElseThrow().value()));
            }
            preflight(tenant,list,source,product,true);
            try {var next=new PlanProductChangeStore.Change(tenant.value(),request.requestKey(),g.root(),id.value(),product,json.writeValueAsString(list),"PENDING",null);changes.insert(next);return next;}
            catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException(ex);}
        });
        if("DONE".equals(c.status()))return view(c);
        if(!changes.claim(c))return new ChangeResult(c.key(),product,"PENDING","删除正在处理，请稍后核查");
        try {
            var list=members(c);idleCreation(tenant,c.root());preflight(tenant,list,source,product,false);
            for(var m:list) {
                var target=member(tenant,m,source);var before=boundProducts(detail(target),m.qcId());
                if(before.containsKey(product)) {
                    if(before.size()<=1)throw new BizException(1403,"关联计划已只剩一件商品，删除已停止，请核查");
                    // A retry always reads membership first: never resend a deletion that has already taken effect.
                    port.deleteProduct(target.credential(),m.qcId(),product);
                }
                if(boundProducts(detail(target),m.qcId()).containsKey(product))throw new BizException(1406,"千川尚未确认商品删除，请稍后核查");
            }
            tx.executeWithoutResult(s->{
                transfers.lockForProductChange(source,c.key());
                for(var m:list) {
                    var plan=plans.findByIdForUpdate(tenant,new PlanId(m.id())).orElseThrow(()->new BizException(1405,"关联计划不存在"));
                    bindings.removeProduct(plan.id(),product);
                    remote.removeProduct(tenant,m.id(),product);
                }
                if(list.stream().noneMatch(m->m.id().equals(c.root()))) {
                    plans.findByIdForUpdate(tenant,new PlanId(c.root())).orElseThrow(()->new BizException(1405,"共享计划不存在"));
                    bindings.removeProduct(new PlanId(c.root()),product);remote.removeProduct(tenant,c.root(),product);
                }
                creation(tenant,c.root()).ifPresent(state->creations.mutate(tenant.value(),c.root(),null,v->removeFromCreation(v,product)));
                changes.complete(c);transfers.finishProductChange(tenant,c.root(),c.key());
                if(TenantContext.tenantId().isPresent())for(var m:list) {
                    var principal=TenantContext.require();audit.record(new SuccessAuditCommand(tenant,principal.userId(),"商家",AuditActionType.PLAN,"PLAN",m.id(),"删除投放商品",null,principal.sessionId().orElse(null),null,json.createObjectNode().put("productId",product).put("detail","同步移除关联转投计划的商品与创意绑定，保留素材中心文件").toString()));
                }
            });
            return new ChangeResult(c.key(),product,"DONE","商品已从关联计划中删除");
        } catch(RuntimeException ex) {
            String message="删除结果待核查："+(ex instanceof BizException?ex.getMessage():"平台或本地处理未完成")+"。点击继续核查，将读取各计划的实际商品绑定后继续处理。";
            changes.release(c,message);return new ChangeResult(c.key(),product,"PENDING",message);
        }
    }
    static void removeFromCreation(CreationState state,String product) {
        Set<String> local=new HashSet<>();state.products.stream().filter(p->product.equals(p.qcProductId())).forEach(p->local.add(p.productId()));local.add(product);
        state.products.removeIf(p->product.equals(p.qcProductId()));
        for(var m:state.materials)if(local.contains(m.productId) && !state.removedMaterialIds.contains(m.id))state.removedMaterialIds.add(m.id);
        state.materials.removeIf(m->local.contains(m.productId));state.generationProductIds.removeIf(local::contains);
        state.recommendations=null;state.mediaChanged();
    }
}
