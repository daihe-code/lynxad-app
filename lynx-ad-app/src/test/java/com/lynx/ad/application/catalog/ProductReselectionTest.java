package com.lynx.ad.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.catalog.application.ProductCommandService;
import com.lynx.ad.modules.catalog.domain.model.Product;
import com.lynx.ad.modules.catalog.domain.port.ProductPoolPort;
import com.lynx.ad.modules.catalog.domain.port.ProductRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductReselectionTest {
    @Test
    void reselectionReusesPersistedProductAndKeepsUserPrice() {
        var repository = mock(ProductRepository.class);
        var pool = mock(ProductPoolPort.class);
        var accounts = mock(OauthAccountRepository.class);
        var tenant = new TenantId("tenant-a");
        var aweme = new AwemeId("aweme-a");
        var qcId = new QcProductId("qp01");
        var rows = new ArrayList<Product>();
        when(accounts.findByTenant(tenant)).thenReturn(List.of());
        when(repository.findByAweme(tenant, aweme)).thenAnswer(invocation -> List.copyOf(rows));
        when(pool.list(any(), eq(aweme.value()))).thenReturn(List.of(
                new ProductPoolPort.PoolItem(qcId, "测试商品", Money.ofYuan(20), null)));
        doAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            rows.removeIf(p -> p.id().equals(product.id()));
            rows.add(product);
            return null;
        }).when(repository).upsert(any());
        var service = new ProductCommandService(repository, pool, accounts);
        var first = service.select(tenant, aweme, List.of(qcId));
        rows.getFirst().changeUserPrice(Money.ofYuan(18));
        var second = service.select(tenant, aweme, List.of(qcId));
        assertEquals(1, rows.size());
        assertEquals(first.products().getFirst().productId(), second.products().getFirst().productId());
        assertEquals("qp01", second.products().getFirst().qcProductId());
        assertEquals(Money.ofYuan(18).amount(), second.products().getFirst().userPrice());
    }
}
