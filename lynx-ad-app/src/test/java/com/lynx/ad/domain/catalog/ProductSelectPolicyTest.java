package com.lynx.ad.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.catalog.domain.model.ProductSelection;
import com.lynx.ad.modules.catalog.domain.service.ProductSelectPolicy;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProductSelectPolicyTest {

    private final ProductSelectPolicy policy = new ProductSelectPolicy();
    private final AwemeId aweme = new AwemeId("aw1");

    @Test
    void zeroRejected() {
        assertThrows(DomainException.class, () -> policy.assertSelectable(new ProductSelection(aweme, List.of())));
    }

    @Test
    void twentyAccepted() {
        List<QcProductId> ids = IntStream.rangeClosed(1, 20).mapToObj(i -> new QcProductId("p" + i)).toList();
        assertDoesNotThrow(() -> policy.assertSelectable(new ProductSelection(aweme, ids)));
    }

    @Test
    void twentyOneRejected() {
        List<QcProductId> ids = IntStream.rangeClosed(1, 21).mapToObj(i -> new QcProductId("p" + i)).toList();
        assertThrows(DomainException.class, () -> policy.assertSelectable(new ProductSelection(aweme, ids)));
    }
}
