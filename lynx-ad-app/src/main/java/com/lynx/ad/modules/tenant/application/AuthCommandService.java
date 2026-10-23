package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.JwtService;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.common.security.RequestMeta;
import com.lynx.ad.common.security.SysRole;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.audit.application.SuccessAuditCommand;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.tenant.api.dto.LoginResponse;
import com.lynx.ad.modules.tenant.api.dto.PeekSmsResponse;
import com.lynx.ad.modules.tenant.api.dto.SendSmsResponse;
import com.lynx.ad.modules.tenant.domain.model.MemberInvitation;
import com.lynx.ad.modules.tenant.domain.model.PasswordHasher;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.port.InvitationStore;
import com.lynx.ad.modules.tenant.domain.port.OtpStore;
import com.lynx.ad.modules.tenant.domain.port.SmsGateway;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import com.lynx.ad.modules.tenant.domain.service.SingleTenantMembershipPolicy;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthCommandService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpStore otpStore;
    private final SmsGateway smsGateway;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final InvitationStore invitationStore;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final LynxAdProperties properties;
    private final AuditRecorder auditRecorder;
    private final PasswordLoginGuard passwordGuard;
    private final AccountInitializationService initialization;
    private final SingleTenantMembershipPolicy membershipPolicy = new SingleTenantMembershipPolicy();

    public AuthCommandService(
            OtpStore otpStore,
            SmsGateway smsGateway,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            InvitationStore invitationStore,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            LynxAdProperties properties,
            AuditRecorder auditRecorder, PasswordLoginGuard passwordGuard,
            AccountInitializationService initialization) {
        this.otpStore = otpStore;
        this.smsGateway = smsGateway;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.invitationStore = invitationStore;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        this.passwordGuard = passwordGuard;
        this.initialization = initialization;
    }

    public SendSmsResponse sendSms(PhoneNumber phone) {
        var sms = properties.getSms();
        if (sms.isOff()) {
            throw new BizException(1112, "短信网关已关闭");
        }
        if (sms.isLive() && !sms.isLiveConfigured()) {
            throw new BizException(1114, "短信服务未配置");
        }
        if (sms.getCooldownSec() > 0 && otpStore.inCooldown(phone)) {
            throw new BizException(1115, "发送过于频繁");
        }
        String code = sms.isLive() ? randomCode() : sms.getDevCode();
        smsGateway.send(phone, code);
        otpStore.save(phone, passwordHasher.hash(code), Duration.ofSeconds(sms.getTtlSec()));
        if (sms.getCooldownSec() > 0) {
            otpStore.markCooldown(phone, Duration.ofSeconds(sms.getCooldownSec()));
        }
        return new SendSmsResponse(sms.getTtlSec(), sms.getCooldownSec());
    }

    public PeekSmsResponse peekSms(PhoneNumber phone, String code) {
        return new PeekSmsResponse(otpStore.peek(phone, code));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse smsLogin(PhoneNumber phone, String code) {
        if (!otpStore.verifyAndConsume(phone, code)) {
            throw new BizException(1101, "验证码不正确或已过期");
        }
        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User created = User.register(phone);
            userRepository.save(created);
            return created;
        });
        claimInviteIfPresent(user, phone);
        passwordGuard.clear(phone.value());
        return issue(user);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse passwordLogin(PhoneNumber phone, String rawPassword) {
        User user = userRepository.findByPhone(phone).orElseThrow(() -> new BizException(1101, "该手机号尚未注册"));
        if (!user.canPasswordLogin() || !user.matchesPassword(rawPassword, passwordHasher)) {
            throw new BizException(1101, "账号或密码错误");
        }
        passwordGuard.clear(phone.value());
        return issue(user);
    }

    @Transactional
    public void resetPassword(PhoneNumber phone, String code, String password) {
        com.lynx.ad.modules.tenant.domain.service.PasswordRules.require(password);
        if (!otpStore.verifyAndConsume(phone, code)) {
            throw new BizException(1101, "验证码不正确或已过期");
        }
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BizException(1101, "该手机号尚未注册"));
        user.setPassword(password, passwordHasher);
        userRepository.save(user);
    }

    public void logout(com.lynx.ad.common.domain.Ids.UserId userId) {
        // short-lived JWT; optional blacklist omitted in this round
    }

    /** Resume sessions issued before workspace creation moved into successful login. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse initializeAccount(UserId userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException(401, "未登录"));
        return session(user, initialization.initialize(userId));
    }

    private void claimInviteIfPresent(User user, PhoneNumber phone) {
        Optional<MemberInvitation> inv = invitationStore.findByPhone(phone);
        if (inv.isEmpty() || inv.get().isExpired(Instant.now())) {
            return;
        }
        Tenant target = tenantRepository.findById(inv.get().tenantId()).orElse(null);
        if (target == null) {
            return;
        }
        Optional<TenantId> existing = tenantRepository.findByUserId(user.id()).map(Tenant::id);
        membershipPolicy.assertCanJoin(existing, target.id());
        if (existing.isEmpty()) {
            target.joinAsMember(user.id(), phone);
            tenantRepository.save(target);
        }
    }

    private LoginResponse issue(User user) {
        Optional<Tenant> tenant = tenantRepository.findByUserId(user.id());
        MemberRole role = tenant.flatMap(t -> t.memberOf(user.id())).map(m -> m.role()).orElse(null);
        String token = jwtService.issueForUser(user, tenant.map(Tenant::id), role);
        boolean needOnboarding = tenant.isEmpty() || user.profile().isEmpty() || user.profile().filter(p -> p.isCompleted()).isEmpty();
        recordLogin(user, tenant.orElse(null), token);
        return new LoginResponse(token, properties.getJwt().getTtlSec(), needOnboarding, properties.isDemoMode());
    }

    private void recordLogin(User user, Tenant tenant, String token) {
        String jti = null;
        try {
            jti = jwtService.parse(token).sessionId().orElse(null);
        } catch (Exception ignored) {
            // ignore
        }
        auditRecorder.record(new SuccessAuditCommand(
                tenant == null ? null : tenant.id(),
                user.id(),
                actorName(user),
                AuditActionType.LOGIN,
                null,
                null,
                "登录",
                RequestMeta.ip().orElse(null),
                jti,
                RequestMeta.userAgent().orElse(null),
                null));
    }

    static String actorName(User user) {
        return user.profile()
                .map(p -> p.nickname())
                .filter(n -> n != null && !n.isBlank())
                .orElseGet(() -> maskPhone(user.phone().value()));
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private static String randomCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
