package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.model.UserProfile;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountInitializationService {
    private final UserRepository users;
    private final TenantRepository tenants;
    private final BillingTrialService trials;
    private WorkspaceFoundation foundation;
    @org.springframework.beans.factory.annotation.Autowired
    public void foundation(WorkspaceFoundation value) { foundation=value; }

    public AccountInitializationService(UserRepository users, TenantRepository tenants, BillingTrialService trials) {
        this.users = users;
        this.tenants = tenants;
        this.trials = trials;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Tenant initialize(UserId userId) {
        // Serialize first use across sessions, then read the latest membership after acquiring the lock.
        User user = users.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> new BizException(401, "未登录"));
        Tenant result=tenants.findByUserId(userId).orElseGet(() -> {
            if (foundation != null && foundation.hasMembership(userId.value()))
                throw new BizException(403, "工作空间成员身份已停用");
            String name = user.profile().map(UserProfile::companyName).filter(value -> !value.isBlank()).orElse("我的工作空间");
            Tenant tenant = Tenant.createForFirstOnboarding(userId, name);
            tenants.save(tenant);
            trials.start(tenant.id(), Instant.now());
            return tenant;
        });
        if(foundation!=null)foundation.ensure(result.id().value());
        return result;
    }
}
