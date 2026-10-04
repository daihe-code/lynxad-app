package com.lynx.ad.domain.tenant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.tenant.domain.service.PasswordRules;
import org.junit.jupiter.api.Test;

class PasswordRulesTest {

    @Test
    void passw0rdStillLegal() {
        assertTrue(PasswordRules.matches("Passw0rd!"));
    }

    @Test
    void rejectsTooShortLettersOnlyDigitsOnly() {
        assertFalse(PasswordRules.matches("Ab1"));
        assertFalse(PasswordRules.matches("abcdefgh"));
        assertFalse(PasswordRules.matches("12345678"));
        DomainException ex = assertThrows(DomainException.class, () -> PasswordRules.require("short"));
        assertTrue(ex.code() == 1002);
    }
}
