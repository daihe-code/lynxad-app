package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.CompanyScale;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.RequestMeta;
import com.lynx.ad.common.security.JwtService;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.audit.application.SuccessAuditCommand;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.tenant.api.dto.CurrentUserResponse;
import com.lynx.ad.modules.tenant.api.dto.UpdatePasswordRequest;
import com.lynx.ad.modules.tenant.api.dto.UpdatePhoneRequest;
import com.lynx.ad.modules.tenant.api.dto.UpdateProfileRequest;
import com.lynx.ad.modules.tenant.domain.model.PasswordHasher;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.port.OtpStore;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountCommandService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordHasher passwordHasher;
    private final OtpStore otpStore;
    private final TenantQuerySupport querySupport;
    private final AuditRecorder auditRecorder;
    private final JwtService jwtService;
    private final PhoneAccess phoneAccess;

    public AccountCommandService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PasswordHasher passwordHasher,
            OtpStore otpStore,
            TenantQuerySupport querySupport,
            AuditRecorder auditRecorder,
            JwtService jwtService, PhoneAccess phoneAccess) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordHasher = passwordHasher;
        this.otpStore = otpStore;
        this.querySupport = querySupport;
        this.auditRecorder = auditRecorder;
        this.jwtService = jwtService;
        this.phoneAccess = phoneAccess;
    }

    @Transactional
    public CurrentUserResponse updateProfile(UserId userId, UpdateProfileRequest req) {
        User user = require(userId);
        user.profileOrCreate()
                .updateAccount(
                        req.nickname(),
                        req.email(),
                        req.companyName(),
                        req.industry(),
                        CompanyScale.parse(req.companyScale()));
        userRepository.save(user);
        record(user, "账户资料");
        return querySupport.current(userId);
    }

    @Transactional
    public AuthenticatedUserUpdate updatePassword(UserId userId, UpdatePasswordRequest req) {
        User user = require(userId);
        user.changePassword(req.oldPassword(), req.newPassword(), passwordHasher);
        userRepository.save(user);
        record(user, "登录密码");
        Tenant tenant = TenantMembershipFacade.currentWorkspace(tenantRepository,userId).orElse(null);
        MemberRole role = tenant == null ? null : tenant.memberOf(userId).map(m -> m.role()).orElse(null);
        String token = jwtService.issueForUser(user, java.util.Optional.ofNullable(tenant).map(Tenant::id), role);
        return new AuthenticatedUserUpdate(querySupport.current(userId), token);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CurrentUserResponse updatePhone(UserId userId, UpdatePhoneRequest req) {
        PhoneNumber original = require(userId).phone();
        PhoneNumber next = new PhoneNumber(req.phone());
        phoneAccess.requireAllowed(next);
        if (!otpStore.verifyAndConsume(next, req.code())) {
            throw new BizException(1101, "验证码不正确或已过期");
        }
        Stream.of(original, next).distinct().sorted(Comparator.comparing(PhoneNumber::value))
                .forEach(phoneAccess::lockAllowed);
        User user = userRepository.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> new BizException(401, "未登录"));
        if (!user.phone().equals(original)) throw new BizException(409, "手机号已更新，请刷新后重试");
        userRepository.findByPhone(next).ifPresent(existing -> {
            if (!existing.id().equals(userId)) {
                throw new BizException(1106, "该手机号已被使用");
            }
        });
        user.changePhone(next);
        userRepository.save(user);
        record(user, "换绑手机");
        return querySupport.current(userId);
    }

    private User require(UserId userId) {
        return userRepository.findById(userId).filter(User::isActive).orElseThrow(() -> new BizException(401, "未登录"));
    }

    private void record(User user, String label) {
        Tenant tenant = TenantMembershipFacade.currentWorkspace(tenantRepository,user.id()).orElse(null);
        String jti = TenantContext.require().sessionId().orElse(null);
        auditRecorder.record(new SuccessAuditCommand(
                tenant == null ? null : tenant.id(),
                user.id(),
                AuthCommandService.actorName(user),
                AuditActionType.ACCOUNT,
                "USER",
                user.id().value(),
                label,
                RequestMeta.ip().orElse(null),
                jti,
                RequestMeta.userAgent().orElse(null),
                null));
    }
}
