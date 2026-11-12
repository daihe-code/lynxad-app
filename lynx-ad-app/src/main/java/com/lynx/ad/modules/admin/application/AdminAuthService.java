package com.lynx.ad.modules.admin.application;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.JwtService;
import com.lynx.ad.common.security.SysRole;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore;
import com.lynx.ad.modules.admin.infrastructure.AdminAccountStore.Account;
import com.lynx.ad.modules.tenant.api.dto.SendSmsResponse;
import com.lynx.ad.modules.tenant.domain.port.OtpStore;
import com.lynx.ad.modules.tenant.domain.port.SmsGateway;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {
    private final AdminAccountStore accounts;
    private final JwtService jwt;
    private final LynxAdProperties.Sms sms;
    private final OtpStore otpStore;
    private final SmsGateway smsGateway;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    private final long ttl;

    public record Me(String id, String username, String displayName, Instant lastLoginAt, String phone,
                     boolean canManageAdmins) {}
    public record Login(String token, long expiresIn, Me admin) {
        @Override public String toString() { return "AdminLogin(expiresIn=" + expiresIn + ")"; }
    }

    public AdminAuthService(AdminAccountStore accounts, JwtService jwt, LynxAdProperties properties,
                            OtpStore otpStore, SmsGateway smsGateway, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.jwt = jwt;
        this.sms = properties.getSms();
        this.otpStore = otpStore;
        this.smsGateway = smsGateway;
        this.encoder = encoder;
        this.ttl = Math.min(7200, properties.getJwt().getTtlSec());
    }

    public SendSmsResponse sendSms(String rawPhone) {
        PhoneNumber phone = phoneNumber(rawPhone);
        authorizedPhone(phone);
        if (!sms.isLive() || !sms.isLiveConfigured()) {
            throw new BizException(1114, "管理员短信服务暂不可用");
        }
        int cooldown = Math.max(60, sms.getCooldownSec());
        if (!otpStore.reserveAdminSms(phone, Duration.ofSeconds(cooldown))) {
            throw new BizException(1115, "验证码发送过于频繁，请稍后再试");
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String hash = encoder.encode(code);
        int validity = Math.clamp(sms.getTtlSec(), 60, 300);
        smsGateway.send(phone, code);
        otpStore.saveAdminLogin(phone, hash, Duration.ofSeconds(validity));
        return new SendSmsResponse(validity, cooldown);
    }

    public Login login(String rawPhone, String code) {
        PhoneNumber phone = phoneNumber(rawPhone);
        Account admin = authorizedPhone(phone);
        if (code == null || !code.matches("[0-9]{6}") || !otpStore.verifyAndConsumeAdminLogin(phone, code)) {
            accounts.audit(admin, "LOGIN", "FAILURE", null, null, "管理员验证码校验未通过");
            throw new BizException(1101, "验证码不正确、已过期或尝试次数过多，请重新获取");
        }
        Instant now = Instant.now();
        String token = jwt.issue(new UserId(admin.id()), Optional.empty(), null, SysRole.ADMIN);
        String session = jwt.parse(token).sessionId().orElseThrow();
        accounts.login(admin, session, now.plusSeconds(ttl), now);
        return new Login(token, ttl, new Me(admin.id(), admin.username(), admin.displayName(), now,
                admin.phone(), admin.canManageAdmins()));
    }

    public Account require() {
        var principal = TenantContext.require();
        if (principal.sysRole() != SysRole.ADMIN) throw new BizException(403, "仅平台管理员可访问");
        var admin = accounts.account(principal.userId().value())
                .orElseThrow(() -> new BizException(403, "管理员身份无效"));
        if (admin.phone() == null || !"ACTIVE".equals(admin.status()) || principal.sessionId().isEmpty()
                || !accounts.validSession(admin.id(), principal.sessionId().get(), Instant.now())) {
            throw new BizException(401, "管理员登录已失效，请重新登录");
        }
        return admin;
    }

    public Me me() { return view(require()); }

    public void logout() { accounts.logout(require(), TenantContext.require().sessionId().orElseThrow()); }

    public List<Me> phoneAdmins() {
        requireGrantor();
        return accounts.phoneAdmins().stream().map(AdminAuthService::view).toList();
    }

    public Me grantPhone(String rawPhone) {
        Account grantor = requireGrantor();
        PhoneNumber phone = phoneNumber(rawPhone);
        try {
            return view(accounts.grantPhone(grantor, phone));
        } catch (DuplicateKeyException duplicate) {
            throw new BizException(1903, "该手机号已获管理员授权");
        }
    }

    private Account requireGrantor() {
        Account admin = require();
        if (!admin.canManageAdmins()) throw new BizException(1904, "当前管理员无权添加管理员手机号");
        return admin;
    }

    private Account authorizedPhone(PhoneNumber phone) {
        return accounts.activePhone(phone).orElseThrow(() -> new BizException(1901, "该手机号未获管理员授权"));
    }

    private static PhoneNumber phoneNumber(String rawPhone) {
        return new PhoneNumber(rawPhone == null ? null : rawPhone.trim());
    }

    private static Me view(Account admin) {
        return new Me(admin.id(), admin.username(), admin.displayName(), admin.lastLoginAt(), admin.phone(),
                admin.canManageAdmins());
    }
}
