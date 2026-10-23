package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.JwtService;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.tenant.api.dto.OnboardingRequest;
import com.lynx.ad.modules.tenant.domain.model.OnboardingCommand;
import com.lynx.ad.modules.tenant.domain.model.PasswordHasher;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import com.lynx.ad.modules.tenant.domain.service.OnboardingPolicy;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingCommandService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordHasher passwordHasher;
    private final BillingTrialService billingTrialService;
    private final JwtService jwtService;
    private final LynxAdProperties properties;
    private final OnboardingPolicy onboardingPolicy = new OnboardingPolicy();
    private final TenantQuerySupport querySupport;

    public OnboardingCommandService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PasswordHasher passwordHasher,
            BillingTrialService billingTrialService,
            JwtService jwtService,
            LynxAdProperties properties,
            TenantQuerySupport querySupport) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordHasher = passwordHasher;
        this.billingTrialService = billingTrialService;
        this.jwtService = jwtService;
        this.properties = properties;
        this.querySupport = querySupport;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CurrentUserResponse complete(UserId userId, OnboardingRequest req) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new BizException(401, "未登录"));
        OnboardingCommand cmd = new OnboardingCommand(
                req.nickname(),
                req.email(),
                req.avatarUrl(),
                req.companyName(),
                req.industry(),
                req.position(),
                req.monthlyBudget(),
                req.adExperience(),
                req.coreDemand(),
                req.password(),
                req.agreeTerms(),
                req.agreePrivacy());
        user.completeOnboarding(cmd);
        if (req.password() != null && !req.password().isBlank()) {
            user.setPassword(req.password(), passwordHasher);
        }
        userRepository.save(user);

        boolean alreadyMember = tenantRepository.findByUserId(userId).isPresent();
        if (onboardingPolicy.shouldCreateTenant(alreadyMember)) {
            Tenant tenant = Tenant.createForFirstOnboarding(userId, req.companyName());
            tenantRepository.save(tenant);
            billingTrialService.start(tenant.id(), Instant.now());
        }
        return new AuthenticatedUserUpdate(querySupport.current(userId), reissueToken(user));
    }

    private String reissueToken(User user) {
        Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
        MemberRole role = tenant == null ? null : tenant.memberOf(user.id()).map(m -> m.role()).orElse(null);
        // Keep this operation's credential snapshot if another reset commits before the response is sent.
        return jwtService.issueForUser(user, java.util.Optional.ofNullable(tenant).map(Tenant::id), role);
    }

    public boolean demoMode() {
        return properties.isDemoMode();
    }
}
