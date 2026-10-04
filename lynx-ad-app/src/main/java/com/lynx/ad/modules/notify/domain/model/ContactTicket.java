package com.lynx.ad.modules.notify.domain.model;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.notify.domain.service.ContactIntakePolicy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class ContactTicket {

    private final String id;
    private final TenantId tenantId;
    private final UserId userId;
    private final PhoneNumber phone;
    private final String source;
    private final String status;
    private final Instant createdAt;

    private ContactTicket(
            String id,
            TenantId tenantId,
            UserId userId,
            PhoneNumber phone,
            String source,
            String status,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.phone = phone;
        this.source = source;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ContactTicket openFromLanding(String rawPhone, Optional<TenantId> tenantId, Optional<UserId> userId) {
        PhoneNumber phone = ContactIntakePolicy.requirePhone(rawPhone);
        return new ContactTicket(
                UUID.randomUUID().toString(),
                tenantId.orElse(null),
                userId.orElse(null),
                phone,
                "LANDING",
                "OPEN",
                Instant.now());
    }

    public String id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UserId userId() {
        return userId;
    }

    public PhoneNumber phone() {
        return phone;
    }

    public String source() {
        return source;
    }

    public String status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
