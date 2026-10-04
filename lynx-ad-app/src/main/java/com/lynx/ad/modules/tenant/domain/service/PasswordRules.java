package com.lynx.ad.modules.tenant.domain.service;

import com.lynx.ad.common.exception.DomainException;
import java.util.regex.Pattern;

public final class PasswordRules {

    private static final Pattern RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");

    private PasswordRules() {}

    public static boolean matches(String raw) {
        return raw != null && RULE.matcher(raw).matches();
    }

    public static void require(String raw) {
        if (!matches(raw)) {
            throw new DomainException(1002, "密码须为 8–20 位且同时包含字母和数字");
        }
    }
}
