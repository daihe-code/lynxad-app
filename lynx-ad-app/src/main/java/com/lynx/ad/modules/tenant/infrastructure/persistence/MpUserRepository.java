package com.lynx.ad.modules.tenant.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.modules.tenant.domain.model.User;
import com.lynx.ad.modules.tenant.domain.model.UserProfile;
import com.lynx.ad.modules.tenant.domain.port.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MpUserRepository implements UserRepository {

    private final UserMapper userMapper;
    private final UserProfileMapper profileMapper;

    public MpUserRepository(UserMapper userMapper, UserProfileMapper profileMapper) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    @Override
    @Transactional
    public void save(User user) {
        UserPo po = userMapper.selectById(user.id().value());
        if (po == null) {
            po = new UserPo();
            po.setId(user.id().value());
            po.setCreatedAt(Instant.now());
            po.setStatus("ACTIVE");
            po.setPhone(user.phone().value());
            po.setPasswordHash(user.passwordHash());
            userMapper.insert(po);
        } else {
            po.setPhone(user.phone().value());
            po.setPasswordHash(user.passwordHash());
            userMapper.updateById(po);
        }
        user.profile().ifPresent(p -> upsertProfile(user.id().value(), p));
    }

    private void upsertProfile(String userId, UserProfile p) {
        UserProfilePo po = profileMapper.selectById(userId);
        boolean insert = po == null;
        if (insert) {
            po = new UserProfilePo();
            po.setUserId(userId);
        }
        po.setNickname(p.nickname());
        po.setEmail(p.email());
        po.setAvatarUrl(p.avatarUrl());
        po.setCompanyName(p.companyName());
        po.setIndustry(p.industry());
        po.setPosition(p.position());
        po.setMonthlyBudget(p.monthlyBudget());
        po.setAdExperience(p.adExperience());
        po.setCoreDemand(p.coreDemand());
        po.setCompanyScale(p.companyScale().valueOrNull());
        po.setCompletedAt(p.completedAt());
        if (insert) {
            profileMapper.insert(po);
        } else {
            profileMapper.updateById(po);
        }
    }

    @Override
    public Optional<User> findByPhone(PhoneNumber phone) {
        UserPo po = userMapper.selectOne(new LambdaQueryWrapper<UserPo>().eq(UserPo::getPhone, phone.value()));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(userMapper.selectById(id.value())).map(this::toDomain);
    }

    private User toDomain(UserPo po) {
        UserProfilePo pp = profileMapper.selectById(po.getId());
        UserProfile profile = pp == null
                ? null
                : UserProfile.restore(
                        pp.getNickname(),
                        pp.getEmail(),
                        pp.getAvatarUrl(),
                        pp.getCompanyName(),
                        pp.getIndustry(),
                        pp.getPosition(),
                        pp.getMonthlyBudget(),
                        pp.getAdExperience(),
                        pp.getCoreDemand(),
                        pp.getCompanyScale(),
                        pp.getCompletedAt());
        return new User(new UserId(po.getId()), new PhoneNumber(po.getPhone()), po.getPasswordHash(), profile, po.getCreatedAt());
    }
}
