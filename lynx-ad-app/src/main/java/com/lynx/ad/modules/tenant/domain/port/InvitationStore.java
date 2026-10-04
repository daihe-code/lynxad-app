package com.lynx.ad.modules.tenant.domain.port;

import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.model.MemberInvitation;
import java.time.Duration;
import java.util.Optional;

public interface InvitationStore {

    void save(MemberInvitation inv, Duration ttl);

    Optional<MemberInvitation> findByPhone(PhoneNumber phone);
}
