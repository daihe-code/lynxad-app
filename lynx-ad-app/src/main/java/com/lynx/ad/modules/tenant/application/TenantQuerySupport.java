package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.api.dto.TrialVo;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.application.SubscriptionQueryService;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.tenant.api.dto.CurrentUserResponse;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.model.UserProfile;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TenantQuerySupport {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final BillingTrialService billingTrialService;
    private final SubscriptionQueryService subscriptionQueryService;
    private final LynxAdProperties properties;

    public TenantQuerySupport(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            BillingTrialService billingTrialService,
            SubscriptionQueryService subscriptionQueryService,
            LynxAdProperties properties) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.billingTrialService = billingTrialService;
        this.subscriptionQueryService = subscriptionQueryService;
        this.properties = properties;
    }

    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    public Optional<Tenant> findById(TenantId id) {
        return tenantRepository.findById(id);
    }

    public CurrentUserResponse current(UserId userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException(401, "未登录"));
        Tenant tenant = tenantRepository.findByUserId(userId).orElse(null);
        TrialVo trial = tenant == null ? null : billingTrialService.current(tenant.id(), Instant.now());
        boolean needOnboarding = tenant == null || user.profile().isEmpty() || user.profile().filter(p -> p.isCompleted()).isEmpty();
        Optional<UserProfile> profile = user.profile();
        long remaining = 0;
        String trialLabel = null;
        if (trial != null && trial.expiresAt() != null) {
            double days = Math.ceil((trial.expiresAt().toEpochMilli() - Instant.now().toEpochMilli()) / 86400000.0);
            remaining = Math.max(0, (long) days);
            trialLabel = trial.active() ? "试用中" : "试用已结束";
        }
        Subscription sub = tenant == null
                ? null
                : subscriptionQueryService.effectiveSubscription(tenant.id(), Instant.now()).orElse(null);
        String displayCode = sub != null ? sub.planCode().name() : tenant == null ? null : "PROFESSIONAL";
        String displayName = sub != null ? sub.planCode().displayName() : tenant == null ? null : "专业版";
        return new CurrentUserResponse(
                user.id().value(),
                user.phone().value(),
                profile.map(UserProfile::nickname).orElse(null),
                tenant == null ? null : tenant.id().value(),
                tenant == null ? null : tenant.name(),
                tenant == null ? null : tenant.memberOf(userId).map(m -> m.role().name()).orElse(null),
                trial == null ? null : trial.expiresAt(),
                trial != null && trial.active(),
                needOnboarding,
                properties.isDemoMode(),
                properties.getMock().isQianchuan() && !properties.getOauth().getQianchuan().isLive(),
                profile.map(UserProfile::email).orElse(null),
                profile.map(UserProfile::companyName).orElse(null),
                profile.map(UserProfile::industry).orElse(null),
                profile.map(p -> p.companyScale().valueOrNull()).orElse(null),
                user.createdAt(),
                user.canPasswordLogin(),
                remaining,
                displayCode,
                displayName,
                trialLabel,
                sub == null ? null : sub.planCode().name(),
                sub == null ? null : sub.planCode().displayName(),
                sub == null ? null : sub.periodStart(),
                sub == null ? null : sub.periodEnd(),
                sub == null ? null : sub.autoRenew(),
                sub == null ? null : sub.payMethod(),
                sub == null ? null : sub.status().name());
    }
}
