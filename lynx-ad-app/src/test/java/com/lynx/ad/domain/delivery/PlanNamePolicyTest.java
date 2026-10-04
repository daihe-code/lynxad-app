package com.lynx.ad.domain.delivery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import com.lynx.ad.modules.delivery.domain.service.PlanNamePolicy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanNamePolicyTest {

    private final PlanNamePolicy policy = new PlanNamePolicy();

    @Test
    void lengthOneToThirty() {
        assertDoesNotThrow(() -> new PlanName("a"));
        assertDoesNotThrow(() -> new PlanName("一二三四五六七八九十".repeat(3)));
        assertThrows(DomainException.class, () -> new PlanName(""));
        assertThrows(DomainException.class, () -> new PlanName("x".repeat(31)));
    }

    @Test
    void duplicateUnfinishedRejected() {
        assertThrows(DomainException.class, () -> policy.assertUnique(true));
        assertDoesNotThrow(() -> policy.assertUnique(false));
    }

    @Test
    void suggestFitsLength() {
        PlanName name = policy.suggest("超长商品名称需要被截断处理的标题", LocalDate.of(2026, 9, 6));
        assertTrue(name.text().length() <= 30);
    }
}
