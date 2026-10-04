package com.lynx.ad.modules.tenant.domain.model;

import com.lynx.ad.common.domain.Ids.NotificationId;
import com.lynx.ad.common.domain.Ids.TenantId;
import java.time.Instant;
import java.util.UUID;

public class InboxMessage {

    private final NotificationId id;
    private final TenantId tenantId;
    private final String title;
    private final String body;
    private final BizRef ref;
    private Instant readAt;
    private final Instant sentAt;

    public InboxMessage(
            NotificationId id,
            TenantId tenantId,
            String title,
            String body,
            BizRef ref,
            Instant readAt,
            Instant sentAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        this.body = body;
        this.ref = ref;
        this.readAt = readAt;
        this.sentAt = sentAt;
    }

    public static InboxMessage post(TenantId tenantId, String title, String body, BizRef ref) {
        return new InboxMessage(
                new NotificationId(UUID.randomUUID().toString()),
                tenantId,
                title,
                body,
                ref,
                null,
                Instant.now());
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public boolean unread() {
        return readAt == null;
    }

    public NotificationId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public BizRef ref() {
        return ref;
    }

    public Instant readAt() {
        return readAt;
    }

    public Instant sentAt() {
        return sentAt;
    }
}
