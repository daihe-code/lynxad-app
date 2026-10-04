package com.lynx.ad.modules.tenant.domain.port;

import com.lynx.ad.common.domain.Ids.NotificationId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.tenant.domain.model.InboxMessage;
import java.util.List;
import java.util.Optional;

public interface InboxMessageRepository {

    void save(InboxMessage msg);

    List<InboxMessage> listByTenant(TenantId id);

    Optional<InboxMessage> findById(TenantId tenantId, NotificationId id);
}
