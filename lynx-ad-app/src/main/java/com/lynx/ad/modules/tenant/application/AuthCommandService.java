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
import java.util.Objects;
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
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final LynxAdProperties properties;
    private final AuditRecorder auditRecorder;
    private final PasswordLoginGuard passwordGuard;
    private final AccountInitializationService initialization;
    private final PhoneAccess phoneAccess;
    private WorkspaceTeamService team;
    @org.springframework.beans.factory.annotation.Autowired
    public void workspaceTeam(WorkspaceTeamService value) { team = value; }
    private org.springframework.jdbc.core.JdbcTemplate sessionJdbc;
    @org.springframework.beans.factory.annotation.Autowired public void sessionJdbc(org.springframework.jdbc.core.JdbcTemplate value) { sessionJdbc=value; }

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
            AccountInitializationService initialization, PhoneAccess phoneAccess) {
        this.otpStore = otpStore;
        this.smsGateway = smsGateway;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        this.passwordGuard = passwordGuard;
        this.initialization = initialization;
        this.phoneAccess = phoneAccess;
    }

    public SendSmsResponse sendSms(PhoneNumber phone) {
        return sendSms(phone, null);
    }

    public SendSmsResponse sendChildActivationSms(String invitationId) {
        return sendSms(team.activationPhone(invitationId), invitationId);
    }

    private SendSmsResponse sendSms(PhoneNumber phone, String invitationId) {
        phoneAccess.requireAllowed(phone);
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
        var hash = passwordHasher.hash(code);
        var ttl = Duration.ofSeconds(sms.getTtlSec());
        if (invitationId == null) otpStore.save(phone, hash, ttl);
        else otpStore.saveChildActivation(invitationId, phone, hash, ttl);
        if (sms.getCooldownSec() > 0) {
            otpStore.markCooldown(phone, Duration.ofSeconds(sms.getCooldownSec()));
        }
        return new SendSmsResponse(sms.getTtlSec(), sms.getCooldownSec());
    }

    public PeekSmsResponse peekSms(PhoneNumber phone, String code) {
        phoneAccess.requireAllowed(phone);
        return new PeekSmsResponse(otpStore.peek(phone, code));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean activateChild(String invitationId, String code, String password) {
        var phone = team.activationPhone(invitationId);
        phoneAccess.requireAllowed(phone);
        com.lynx.ad.modules.tenant.domain.service.PasswordRules.require(password);
        if (code == null || !code.matches("[0-9]{6}") || !otpStore.verifyAndConsumeChildActivation(invitationId, phone, code)) {
            throw new BizException(1101, "验证码不正确或已过期");
        }
        phoneAccess.lockAllowed(phone);
        return team.activateFromWorkspace(invitationId, phone, password, passwordHasher);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse smsLogin(PhoneNumber phone, String code) {
        return smsLogin(phone, code, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse smsLogin(PhoneNumber phone, String code, String invite) {
        phoneAccess.requireAllowed(phone);
        if (!otpStore.verifyAndConsume(phone, code)) {
            throw new BizException(1101, "验证码不正确或已过期");
        }
        phoneAccess.lockAllowed(phone);
        if (invite != null && !invite.isBlank()) {
            var joined = team.activate(phone, invite);
            var tenant = tenantRepository.findById(new TenantId(joined.tenant())).orElseThrow();
            passwordGuard.clear(phone.value());
            LoginResponse result = session(joined.user(), tenant);
            recordLogin(joined.user(), tenant, result.token());
            return result;
        }
        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User created = User.register(phone);
            userRepository.save(created);
            return created;
        });
        passwordGuard.clear(phone.value());
        return issue(user);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse passwordLogin(PhoneNumber phone, String rawPassword) {
        phoneAccess.requireAllowed(phone);
        passwordGuard.check(phone.value());
        User user = userRepository.findByPhone(phone).orElseThrow(() -> new BizException(1101, "该手机号尚未注册"));
        requireActive(user);
        if (!user.canPasswordLogin() || !user.matchesPassword(rawPassword, passwordHasher)) {
            passwordGuard.failed(phone.value());
            throw new BizException(1101, "账号或密码错误");
        }
        phoneAccess.lockAllowed(phone);
        User current = userRepository.findByIdForUpdate(user.id())
                .filter(value -> value.phone().equals(phone))
                .orElseThrow(() -> new BizException(1101, "账号或密码错误"));
        requireActive(current);
        if (!Objects.equals(user.passwordHash(), current.passwordHash())) {
            throw new BizException(1101, "账号或密码错误");
        }
        passwordGuard.clear(phone.value());
        return issue(current);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resetPassword(PhoneNumber phone, String code, String password) {
        phoneAccess.requireAllowed(phone);
        com.lynx.ad.modules.tenant.domain.service.PasswordRules.require(password);
        if (!otpStore.verifyAndConsume(phone, code)) {
            throw new BizException(1101, "验证码不正确或已过期");
        }
        phoneAccess.lockAllowed(phone);
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new BizException(1101, "该手机号尚未注册"));
        requireActive(user);
        user.setPassword(password, passwordHasher);
        userRepository.save(user);
    }

    public void logout(com.lynx.ad.common.domain.Ids.UserId userId) {
        if(sessionJdbc==null)return;
        var principal=com.lynx.ad.common.security.TenantContext.require();
        principal.sessionId().ifPresent(id->{
            // Same session can be logged out repeatedly; its JWT lifetime bounds retention.
            try {
                sessionJdbc.update("INSERT INTO workspace_session_revocation(session_id,expires_at) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM workspace_session_revocation WHERE session_id=?)",id,java.sql.Timestamp.from(Instant.now().plusSeconds(properties.getJwt().getTtlSec())),id);
            } catch (org.springframework.dao.DuplicateKeyException alreadyRevoked) {
                // Another replica already invalidated the same session.
            }
        });
    }

    /** Resume sessions issued before workspace creation moved into successful login. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse initializeAccount(UserId userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException(401, "未登录"));
        return session(user, initialization.initialize(userId));
    }

    private LoginResponse issue(User user) {
        requireActive(user);
        Tenant tenant = initialization.initialize(user.id());
        LoginResponse response = session(user, tenant);
        recordLogin(user, tenant, response.token());
        return response;
    }

    private static void requireActive(User user) {
        if (!user.isActive()) throw new BizException(1121, "该手机号已被限制注册或登录");
    }

    private LoginResponse session(User user, Tenant tenant) {
        MemberRole role = tenant.memberOf(user.id()).orElseThrow(() -> new BizException(403, "非本公司成员")).role();
        String token = jwtService.issueForUser(user, Optional.of(tenant.id()), role);
        return new LoginResponse(token, properties.getJwt().getTtlSec(), false, properties.isDemoMode());
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
