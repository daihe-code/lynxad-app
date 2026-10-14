package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.api.dto.BillItemView;
import com.lynx.ad.modules.billing.api.dto.BillsView;
import com.lynx.ad.modules.billing.api.dto.PlanItemView;
import com.lynx.ad.modules.billing.api.dto.PlansView;
import com.lynx.ad.modules.billing.api.dto.SubscriptionView;
import com.lynx.ad.modules.billing.domain.model.BillingOrder;
import com.lynx.ad.modules.billing.domain.model.BillingPlan;
import com.lynx.ad.modules.billing.domain.model.OrderType;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.port.OrderRepository;
import com.lynx.ad.modules.billing.domain.port.PlanRepository;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import com.lynx.ad.modules.billing.api.dto.TrialVo;
import com.lynx.ad.modules.billing.domain.service.PeriodPolicy;
import com.lynx.ad.modules.billing.domain.service.PricePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionQueryService {

    private final PlanRepository planRepository;
    private final OrderRepository orderRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSupport subscriptionSupport;
    private final BillingTrialService billingTrialService;
    private final LynxAdProperties properties;
    private final PricePolicy pricePolicy = new PricePolicy();

    public SubscriptionQueryService(
            PlanRepository planRepository,
            OrderRepository orderRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionSupport subscriptionSupport,
            BillingTrialService billingTrialService,
            LynxAdProperties properties) {
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionSupport = subscriptionSupport;
        this.billingTrialService = billingTrialService;
        this.properties = properties;
    }

    public PlansView listPlans(TenantId tenantId) {
        Instant now = Instant.now();
        Optional<Subscription> effective = subscriptionSupport.effective(tenantId, now);
        PlanCode current = effective.map(Subscription::planCode).orElse(null);
        List<PlanItemView> items = planRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.sortNo(), b.sortNo()))
                .map(p -> toPlanItem(p, current, effective.isPresent()))
                .toList();
        return new PlansView(items);
    }

    public SubscriptionView current(TenantId tenantId) {
        Instant now = Instant.now();
        Optional<Subscription> effective = subscriptionSupport.effective(tenantId, now);
        PeriodPolicy periods = periods();
        if (effective.isPresent()) {
            Subscription sub = effective.get();
            BillingPlan plan = planRepository.findByCode(sub.planCode()).orElseThrow();
            boolean hadPaid = orderRepository.hasPaidPlan(tenantId, sub.planCode());
            int first = pricePolicy.firstMonthPayableFen(plan.listPriceFen(), hadPaid);
            String discount = pricePolicy.firstMonthLabel(plan.listPriceFen(), first);
            return new SubscriptionView(
                    true,
                    false,
                    sub.planCode().name(),
                    plan.name(),
                    MoneyYuan.ofFen(plan.listPriceFen()),
                    discount,
                    sub.periodStart(),
                    sub.periodEnd(),
                    periods.remainingDays(now, sub.periodEnd()),
                    sub.autoRenew(),
                    sub.payMethod(),
                    sub.status().name());
        }
        TrialVo trial = billingTrialService.current(tenantId, now);
        int remaining = 0;
        if (trial != null && trial.expiresAt() != null) {
            remaining = (int) Math.max(0, Math.ceil((trial.expiresAt().toEpochMilli() - now.toEpochMilli()) / 86400000.0));
        }
        return new SubscriptionView(
                false,
                true,
                PlanCode.PROFESSIONAL.name(),
                PlanCode.PROFESSIONAL.displayName(),
                MoneyYuan.ofFen(79900),
                null,
                trial == null ? null : null,
                trial == null ? null : trial.expiresAt(),
                remaining,
                null,
                null,
                trial != null && trial.active() ? "TRIAL" : "TRIAL_EXPIRED");
    }

    @org.springframework.transaction.annotation.Transactional
    public SubscriptionView patchAutoRenew(TenantId tenantId, boolean autoRenew) {
        Instant now = Instant.now();
        Subscription sub = subscriptionSupport
                .effective(tenantId, now)
                .orElseThrow(() -> new BizException(1604, "当前没有有效订阅"));
        sub.setAutoRenew(autoRenew, now);
        subscriptionRepository.save(sub);
        return current(tenantId);
    }

    public BillsView listBills(TenantId tenantId) {
        Instant now = Instant.now();
        List<BillItemView> items = new ArrayList<>();
        for (BillingOrder order : orderRepository.findPaidWithAmount(tenantId)) {
            items.add(toBill(order));
        }
        Optional<Subscription> effective = subscriptionSupport.effective(tenantId, now);
        if (effective.isPresent() && effective.get().autoRenew()) {
            Subscription sub = effective.get();
            PlanCode next = sub.nextPlanCode() == null ? sub.planCode() : sub.nextPlanCode();
            BillingPlan plan = planRepository.findByCode(next).orElseThrow();
            boolean hadPaid = orderRepository.hasPaidPlan(tenantId, next);
            int payable = pricePolicy.firstMonthPayableFen(plan.listPriceFen(), hadPaid);
            PeriodPolicy periods = periods();
            LocalDate debit = periods.nextDebitDate(sub.periodEnd());
            items.add(new BillItemView(
                    "preview-next",
                    debit.toString(),
                    plan.name() + " · 月度订阅（自动续费）",
                    payable,
                    MoneyYuan.ofFen(payable),
                    null,
                    "WECHAT",
                    "PENDING_DEBIT",
                    false));
        }
        return new BillsView(items);
    }

    public Optional<Subscription> effectiveSubscription(TenantId tenantId, Instant now) {
        return subscriptionSupport.effective(tenantId, now);
    }

    private PlanItemView toPlanItem(BillingPlan plan, PlanCode current, boolean paid) {
        boolean isCurrent = paid && current == plan.code();
        String action;
        if (!paid) {
            action = "SUBSCRIBE";
        } else if (isCurrent) {
            action = "CURRENT";
        } else if (plan.code().higherThan(current)) {
            action = "UPGRADE";
        } else {
            action = "DOWNGRADE";
        }
        return new PlanItemView(
                plan.code().name(),
                plan.name(),
                plan.listPriceFen(),
                MoneyYuan.ofFen(plan.listPriceFen()),
                plan.adAccountLimit(),
                plan.suitableFor(),
                plan.benefits(),
                isCurrent,
                action);
    }

    private BillItemView toBill(BillingOrder order) {
        String title;
        if (order.type() == OrderType.UPGRADE) {
            String from = order.fromPlanCode() == null ? "" : order.fromPlanCode().displayName();
            title = from + " → " + order.planCode().displayName() + " · 差价补缴";
        } else if (order.discountLabel() != null && !order.discountLabel().isBlank()) {
            title = order.planCode().displayName() + " · 月度订阅";
        } else {
            title = order.planCode().displayName() + " · 首次订阅";
        }
        String date = order.paidAt() == null
                ? ""
                : LocalDate.ofInstant(order.paidAt(), periods().zone()).toString();
        return new BillItemView(
                order.id(),
                date,
                title,
                order.payableFen(),
                MoneyYuan.ofFen(order.payableFen()),
                order.discountLabel(),
                "WECHAT",
                "PAID",
                true);
    }

    private PeriodPolicy periods() {
        return new PeriodPolicy(ZoneId.of(properties.getPayment().getTimezone()));
    }
}
