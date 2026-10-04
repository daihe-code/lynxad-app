package com.lynx.ad.modules.notify.domain.service;

import com.lynx.ad.common.domain.PhoneNumber;

public final class ContactIntakePolicy {

    private ContactIntakePolicy() {}

    public static PhoneNumber requirePhone(String raw) {
        return new PhoneNumber(raw);
    }
}
