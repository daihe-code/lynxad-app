package com.lynx.ad.modules.tenant.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface UserMapper extends BaseMapper<UserPo> {}

@Mapper
interface UserProfileMapper extends BaseMapper<UserProfilePo> {}

@Mapper
interface TenantMapper extends BaseMapper<TenantPo> {}

@Mapper
interface TenantMemberMapper extends BaseMapper<TenantMemberPo> {}

@Mapper
interface NotificationMapper extends BaseMapper<NotificationPo> {}
