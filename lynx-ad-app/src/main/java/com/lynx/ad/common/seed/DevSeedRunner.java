package com.lynx.ad.common.seed;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.QcPlanId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.catalog.domain.model.Product;
import com.lynx.ad.modules.catalog.domain.port.ProductRepository;
import com.lynx.ad.modules.catalog.infrastructure.adapter.MockProductPool;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.model.AudienceJson;
import com.lynx.ad.modules.delivery.domain.model.DeliverySetting;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import com.lynx.ad.modules.delivery.domain.model.RoiGoal;
import com.lynx.ad.modules.delivery.domain.model.Schedule;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.identity.application.OauthCommandService;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.tenant.domain.model.OnboardingCommand;
import com.lynx.ad.modules.tenant.domain.model.PasswordHasher;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !test")
public class DevSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordHasher passwordHasher;
    private final BillingTrialService billingTrialService;
    private final OauthCommandService oauthCommandService;
    private final OauthAccountRepository oauthAccountRepository;
    private final ProductRepository productRepository;
    private final AdPlanRepository planRepository;

    public DevSeedRunner(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PasswordHasher passwordHasher,
            BillingTrialService billingTrialService,
            OauthCommandService oauthCommandService,
            OauthAccountRepository oauthAccountRepository,
            ProductRepository productRepository,
            AdPlanRepository planRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordHasher = passwordHasher;
        this.billingTrialService = billingTrialService;
        this.oauthCommandService = oauthCommandService;
        this.oauthAccountRepository = oauthAccountRepository;
        this.productRepository = productRepository;
        this.planRepository = planRepository;
    }

    @Override
    public void run(String... args) {
        PhoneNumber phone = new PhoneNumber("13800000001");
        User user = userRepository.findByPhone(phone).orElseGet(() -> createDemoUser(phone));
        Tenant tenant = tenantRepository.findByUserId(user.id()).orElseGet(() -> createDemoTenant(user));
        billingTrialService.reopenExpiredForDevSeed(tenant.id(), Instant.now());
        if (oauthAccountRepository.findByTenant(tenant.id()).isEmpty()) {
            oauthCommandService.mockBind(tenant.id());
        }
        var account = oauthAccountRepository.findByTenant(tenant.id()).stream().findFirst().orElseThrow();
        AwemeId aweme = account.primaryAweme().map(a -> a.awemeId()).orElse(new AwemeId("aweme_official"));
        if (productRepository.findByAweme(tenant.id(), aweme).isEmpty()) {
            for (var item : MockProductPool.bakery()) {
                productRepository.upsert(Product.fromPool(tenant.id(), aweme, item.id(), item.name(), item.price(), item.imageUrl()));
            }
        }
        if (planRepository.findByTenant(tenant.id()).isEmpty()) {
            seedPlan(tenant.id(), account.id(), aweme, "烘焙放量计划", true, false);
            seedPlan(tenant.id(), account.id(), aweme, "外部对照计划", false, false);
            seedPlan(tenant.id(), account.id(), aweme, "审核拒绝计划", true, true);
        }
        log.info("dev seed ready phone=13800000001");
    }

    private User createDemoUser(PhoneNumber phone) {
        User user = User.register(phone);
        user.setPassword("Passw0rd!", passwordHasher);
        user.completeOnboarding(new OnboardingCommand(
                "演示店主",
                "demo@lynx.ad",
                null,
                "演示烘焙店",
                "食品",
                "店主",
                "1-3万",
                "新手",
                "测闭环",
                "Passw0rd!",
                true,
                true));
        userRepository.save(user);
        return user;
    }

    private Tenant createDemoTenant(User user) {
        Tenant tenant = Tenant.createForFirstOnboarding(user.id(), "演示烘焙店");
        tenantRepository.save(tenant);
        return tenant;
    }

    private void seedPlan(TenantId tenantId, AccountId accountId, AwemeId aweme, String name, boolean lingtou, boolean rejected) {
        AdPlan plan = AdPlan.draft(
                tenantId,
                accountId,
                aweme,
                new PlanName(name),
                new DeliverySetting(SmartBidType.CONSERVATIVE, Money.ofYuan(300), Optional.of(new RoiGoal(new BigDecimal("4"))), Schedule.fromNow(), AudienceJson.empty()),
                List.of(new QcProductId("qp01")));
        if (lingtou && !rejected) {
            plan.publish(stubPkg());
            plan.acceptExternalId(new QcPlanId("mock_seed1"));
            plan.markDelivering();
        } else if (rejected) {
            plan.publish(stubPkg());
            plan.acceptExternalId(new QcPlanId("mock_seed_rej"));
            plan.markRejected("演示拒绝");
        } else {
            // EXTERNAL display row: keep as draft locally; list merge may add ocean rows
            plan.apply(com.lynx.ad.modules.delivery.domain.model.PlanStatus.DELIVERING);
        }
        planRepository.save(plan);
    }

    private com.lynx.ad.modules.creative.domain.model.CreativePackage stubPkg() {
        var pkg = com.lynx.ad.modules.creative.domain.model.CreativePackage.of(List.of(
                new com.lynx.ad.modules.creative.domain.model.CreativeArtifact(
                        new QcProductId("qp01"),
                        List.of(new com.lynx.ad.modules.creative.domain.model.AdTitle(
                                "演示标题", com.lynx.ad.modules.creative.domain.model.TitleType.CUSTOM)),
                        Optional.empty(),
                        Optional.empty())));
        pkg.confirm();
        return pkg;
    }
}
