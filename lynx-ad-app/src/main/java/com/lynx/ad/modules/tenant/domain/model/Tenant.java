package com.lynx.ad.modules.tenant.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.common.security.MemberRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Tenant {

    private final TenantId id;
    private final String name;
    private final String status;
    private final List<TenantMember> members;

    public Tenant(TenantId id, String name, String status, List<TenantMember> members) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.members = new ArrayList<>(members == null ? List.of() : members);
    }

    public static Tenant createForFirstOnboarding(UserId ownerId, String companyName) {
        if (companyName == null || companyName.isBlank()) {
            throw new DomainException(1001, "公司名称不能为空");
        }
        Tenant tenant = new Tenant(new TenantId(UUID.randomUUID().toString()), companyName.trim(), "ACTIVE", new ArrayList<>());
        tenant.members.add(new TenantMember(ownerId, MemberRole.OWNER, Instant.now()));
        return tenant;
    }

    public MemberInvitation invite(UserId actor, PhoneNumber phone) {
        TenantMember member = memberOf(actor).orElseThrow(() -> new DomainException(403, "非本公司成员"));
        if (!member.canInvite()) {
            throw new DomainException(403, "仅主账号可邀请同事");
        }
        return new MemberInvitation(phone, id, Instant.now().plusSeconds(7 * 24 * 3600));
    }

    public void joinAsMember(UserId userId, PhoneNumber phone) {
        if (memberOf(userId).isPresent()) {
            return;
        }
        members.add(new TenantMember(userId, MemberRole.MEMBER, Instant.now()));
    }

    public Optional<TenantMember> memberOf(UserId userId) {
        return members.stream().filter(m -> m.userId().equals(userId)).findFirst();
    }

    public List<TenantMember> members() {
        return List.copyOf(members);
    }

    public TenantId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String status() {
        return status;
    }
}
