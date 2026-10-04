package com.lynx.ad.domain.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.notify.domain.service.ContactIntakePolicy;
import org.junit.jupiter.api.Test;

class ContactIntakePolicyTest {

    @Test
    void validPhoneOk() {
        assertEquals("13900001111", ContactIntakePolicy.requirePhone("13900001111").value());
    }

    @Test
    void invalidPhone1001() {
        DomainException ex = assertThrows(DomainException.class, () -> ContactIntakePolicy.requirePhone("123"));
        assertEquals(1001, ex.code());
    }
}
