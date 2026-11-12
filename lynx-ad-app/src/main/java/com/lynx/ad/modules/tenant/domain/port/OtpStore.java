package com.lynx.ad.modules.tenant.domain.port;

import com.lynx.ad.common.domain.PhoneNumber;
import java.time.Duration;

public interface OtpStore {

    void save(PhoneNumber phone, String codeHash, Duration ttl);

    boolean verifyAndConsume(PhoneNumber phone, String code);

    void saveChildActivation(String invitationId, PhoneNumber phone, String codeHash, Duration ttl);

    boolean verifyAndConsumeChildActivation(String invitationId, PhoneNumber phone, String code);

    boolean reserveAdminSms(PhoneNumber phone, Duration cooldown);

    void saveAdminLogin(PhoneNumber phone, String codeHash, Duration ttl);

    boolean verifyAndConsumeAdminLogin(PhoneNumber phone, String code);

    boolean peek(PhoneNumber phone, String code);

    boolean inCooldown(PhoneNumber phone);

    void markCooldown(PhoneNumber phone, Duration ttl);
}
