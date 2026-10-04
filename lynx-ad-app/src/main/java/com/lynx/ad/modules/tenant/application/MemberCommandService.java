package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.tenant.api.dto.MemberVo;
import com.lynx.ad.modules.tenant.domain.model.MemberInvitation;
import com.lynx.ad.modules.tenant.domain.model.Tenant;
import com.lynx.ad.modules.tenant.domain.port.InvitationStore;
import com.lynx.ad.modules.tenant.domain.port.TenantRepository;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemberCommandService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final InvitationStore invitationStore;

    public MemberCommandService(
            TenantRepository tenantRepository, UserRepository userRepository, InvitationStore invitationStore) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.invitationStore = invitationStore;
    }

    public void invite(UserId actor, PhoneNumber phone) {
        Tenant tenant = tenantRepository.findByUserId(actor).orElseThrow(() -> new BizException(401, "未开通公司"));
        MemberInvitation invitation = tenant.invite(actor, phone);
        invitationStore.save(invitation, Duration.ofDays(7));
    }

    public List<MemberVo> list(TenantId tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BizException(1001, "公司不存在"));
        return tenant.members().stream()
                .map(m -> new MemberVo(
                        m.userId().value(),
                        userRepository.findById(m.userId()).map(u -> u.phone().value()).orElse(""),
                        m.role().name(),
                        m.joinedAt()))
                .toList();
    }
}
