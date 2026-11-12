package com.lynx.ad.modules.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.identity.application.AccountBindingRegistry;
import com.lynx.ad.modules.tenant.application.WorkspaceFoundation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The app owns charging; provider retries never create another customer reservation. */
@Service
public class GenerationCharges {
    private final WorkspacePricing pricing;
    private final WorkspacePointOrders credits;
    private final WorkspaceFoundation foundation;
    private final PointWalletService wallet;
    private final AccountBindingRegistry bindings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SubscriptionSupport subscriptions;

    public GenerationCharges(WorkspacePricing pricing, WorkspacePointOrders credits, WorkspaceFoundation foundation,
            PointWalletService wallet, AccountBindingRegistry bindings, JdbcTemplate jdbc, ObjectMapper json,
            SubscriptionSupport subscriptions) {
        this.pricing = pricing; this.credits = credits; this.foundation = foundation; this.wallet = wallet;
        this.bindings = bindings; this.jdbc = jdbc; this.json = json; this.subscriptions = subscriptions;
    }

    public record Quote(AccountBindingRegistry.ExecutionScope scope, Map<String,Integer> requested, Map<String,BigDecimal> rates) {}

    public enum BalanceStatus { SUFFICIENT, LOW, EMPTY }
    public record Access(boolean enabled, BalanceStatus balanceStatus) {}

    public Access access(String tenant) {
        if (!pricing.isEnabled()) return new Access(false, null);
        var sub = subscriptions.effective(new TenantId(tenant), Instant.now());
        if (sub.isEmpty()) return new Access(false, null);
        credits.ensureCurrentGrant(tenant);
        BigDecimal available = wallet.balance(tenant).available();
        BigDecimal threshold = BigDecimal.valueOf(pricing.points(sub.get().planCode().name()))
                .movePointLeft(1).max(pricing.rate("video"));
        BalanceStatus status = available.signum() <= 0 ? BalanceStatus.EMPTY
                : available.compareTo(threshold) <= 0 ? BalanceStatus.LOW : BalanceStatus.SUFFICIENT;
        return new Access(true, status);
    }

    public void requireAccess(String tenant) {
        if (subscriptions.effective(new TenantId(tenant), Instant.now()).isEmpty())
            throw new BizException(1904, "智能生成素材需先开通套餐");
        if (!pricing.isEnabled()) throw new BizException(1904, "智能生成暂不可用");
    }

    @Transactional
    public boolean hold(String tenant,String task,String binding,String fallbackActor,Map<String,Integer> requested) {
        foundation.ensure(tenant);
        requireAccess(tenant);
        String actor = TenantContext.tenantId().filter(t -> t.value().equals(tenant))
                .map(t -> TenantContext.userId().value()).orElse(fallbackActor);
        var scope = bindings.executionScope(tenant,binding,actor);
        bindings.validatePaid(scope);
        var previous = quote(tenant,task);
        if (previous != null) {
            if (!previous.scope().equals(scope) || !previous.requested().equals(requested))
                throw new BizException(1902,"生成任务的积分预占信息已变化");
            return true;
        }
        var rates = new TreeMap<String,BigDecimal>();
        requested.forEach((type,count) -> {
            if (count <= 0) throw new IllegalArgumentException("Generation quantity must be positive");
            rates.put(type,pricing.rate(type));
        });
        var quote = new Quote(scope,new TreeMap<>(requested),rates);
        BigDecimal value = total(quote,requested);
        credits.ensureCurrentGrant(tenant);
        wallet.reserve(tenant,task,binding,scope.actorUserId(),value);
        try { jdbc.update("INSERT INTO workspace_generation_charge(tenant_id,task_key,rates_json) VALUES(?,?,?)",tenant,task,json.writeValueAsString(quote)); }
        catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        return true;
    }

    public void validate(String tenant,String task) {
        requireAccess(tenant);
        var q = task == null ? null : quote(tenant,task);
        if (q == null || wallet.reservation(tenant,task).filter(r -> "HELD".equals(r.status())).isEmpty())
            throw new BizException(1903, "生成任务尚未预占积分，请重新提交");
        bindings.validatePaid(q.scope());
    }

    @Transactional
    public void finish(String tenant,String task,Map<String,Integer> delivered) {
        if (task == null) return;
        var quote = quote(tenant,task);
        if (quote == null) return;
        if (total(quote,quote.requested()).signum() == 0) return;
        foundation.ensure(tenant);
        if (wallet.reservation(tenant,task).filter(r -> "HELD".equals(r.status())).isEmpty()) return;
        BigDecimal actual = total(quote,delivered);
        if (actual.signum() == 0) wallet.release(tenant,task);
        else wallet.settle(tenant,task,actual);
    }

    public Quote quote(String tenant,String task) {
        return jdbc.query("SELECT rates_json FROM workspace_generation_charge WHERE tenant_id=? AND task_key=?",(r,i) -> {
            try { return json.readValue(r.getString(1),Quote.class); }
            catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        },tenant,task).stream().findFirst().orElse(null);
    }

    private static BigDecimal total(Quote quote,Map<String,Integer> counts) {
        return quote.requested().entrySet().stream().map(e -> quote.rates().get(e.getKey()).multiply(
                BigDecimal.valueOf(Math.clamp(counts.getOrDefault(e.getKey(),0),0,e.getValue()))))
                .reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    public static String kind(String type) {
        return switch (type) {
            case "VIDEO","video" -> "video";
            case "IMAGE","product_card" -> "image";
            case "CAROUSEL","image_text" -> "native_image_text";
            case "title" -> "title";
            default -> throw new IllegalArgumentException("Unsupported generation type");
        };
    }
}
