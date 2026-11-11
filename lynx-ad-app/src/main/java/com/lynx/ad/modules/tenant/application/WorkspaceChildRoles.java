package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.MemberRole;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class WorkspaceChildRoles {
    private static final List<MemberRole> CHILD_ROLES = List.of(MemberRole.OPERATOR, MemberRole.VIEWER, MemberRole.FINANCE);

    private WorkspaceChildRoles() {}

    static List<MemberRole> require(List<MemberRole> roles) {
        if (roles == null || roles.isEmpty() || roles.stream().anyMatch(role -> role == null || !CHILD_ROLES.contains(role)))
            throw new BizException(1911, "请选择有效的子账户权限");
        return CHILD_ROLES.stream().filter(roles::contains).toList();
    }

    static List<MemberRole> read(String stored, MemberRole legacy) {
        if (stored == null || stored.isBlank()) return List.of(legacy);
        return require(Arrays.stream(stored.split(",")).map(MemberRole::valueOf).toList());
    }

    static String write(List<MemberRole> roles) {
        return roles.stream().map(MemberRole::name).collect(Collectors.joining(","));
    }
}
