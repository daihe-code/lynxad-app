package com.lynx.ad.modules.tenant.domain.port;

import com.lynx.ad.common.domain.PhoneNumber;
import java.time.Duration;

public interface OtpStore {

    void save(PhoneNumber phone, String codeHash, Duration ttl);

    boolean verifyAndConsume(PhoneNumber phone, String code);

    boolean peek(PhoneNumber phone, String code);

    boolean inCooldown(PhoneNumber phone);

    void markCooldown(PhoneNumber phone, Duration ttl);
}
