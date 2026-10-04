package com.lynx.ad.common.domain;

import com.lynx.ad.common.exception.DomainException;
import java.util.regex.Pattern;

public record PhoneNumber(String value) {

    private static final Pattern CN = Pattern.compile("^1[3-9]\\d{9}$");

    public PhoneNumber {
        if (value == null || !CN.matcher(value).matches()) {
            throw new DomainException(1001, "手机号格式不正确");
        }
    }
}
