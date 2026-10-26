package com.lynx.ad.modules.tenant.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lynx.ad.common.domain.Ids.NotificationId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.domain.model.InboxMessage;
import com.lynx.ad.modules.tenant.domain.port.InboxMessageRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class MpInboxMessageRepository implements InboxMessageRepository {

    private final NotificationMapper mapper;

    public MpInboxMessageRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(InboxMessage msg) {
        NotificationPo po = mapper.selectById(msg.id().value());
        boolean insert = po == null;
        if (insert) {
            po = new NotificationPo();
            po.setId(msg.id().value());
            po.setTenantId(msg.tenantId().value());
            po.setChannel("INBOX");
            po.setSentAt(msg.sentAt());
        }
        po.setTitle(msg.title());
        po.setBody(msg.body());
        if (msg.ref() != null) {
            po.setBizType(msg.ref().bizType());
            po.setBizId(msg.ref().bizId());
            po.setResultJson(InboxResultJson.write(msg.ref().result()));
        }
        po.setReadAt(msg.readAt());
        if (insert) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public List<InboxMessage> listByTenant(TenantId id) {
        return mapper.selectList(new LambdaQueryWrapper<NotificationPo>()
                        .eq(NotificationPo::getTenantId, id.value())
                        .eq(NotificationPo::getChannel, "INBOX")
                        .orderByDesc(NotificationPo::getSentAt))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<InboxMessage> findById(TenantId tenantId, NotificationId id) {
        NotificationPo po = mapper.selectById(id.value());
        if (po == null || !tenantId.value().equals(po.getTenantId()) || !"INBOX".equals(po.getChannel())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public int markAllRead(TenantId tenantId, Instant before) {
        return mapper.update(null, new LambdaUpdateWrapper<NotificationPo>()
                .eq(NotificationPo::getTenantId, tenantId.value()).eq(NotificationPo::getChannel, "INBOX")
                .isNull(NotificationPo::getReadAt).le(NotificationPo::getSentAt, before)
                .set(NotificationPo::getReadAt, Instant.now()));
    }

    private InboxMessage toDomain(NotificationPo po) {
        return new InboxMessage(
                new NotificationId(po.getId()),
                new TenantId(po.getTenantId()),
                po.getTitle(),
                po.getBody(),
                new BizRef(po.getBizType(), po.getBizId(), InboxResultJson.read(po.getResultJson())),
                po.getReadAt(),
                po.getSentAt());
    }
}
