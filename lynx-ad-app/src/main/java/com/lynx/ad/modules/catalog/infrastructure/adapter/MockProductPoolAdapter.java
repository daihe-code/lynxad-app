package com.lynx.ad.modules.catalog.infrastructure.adapter;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.catalog.domain.port.ProductPoolPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "lynx.ad.mock", name = "qianchuan", havingValue = "true", matchIfMissing = true)
public class MockProductPoolAdapter implements ProductPoolPort {

    public static List<PoolItem> bakery() {
        return List.of(
                item("qp01", "黄油可颂", "12.80"),
                item("qp02", "脏脏包", "16.90"),
                item("qp03", "草莓蛋糕", "38.00"),
                item("qp04", "巧克力磅蛋糕", "28.50"),
                item("qp05", "海盐卷", "9.90"),
                item("qp06", "抹茶欧包", "14.20"),
                item("qp07", "肉松小贝", "11.50"),
                item("qp08", "提拉米苏", "32.00"));
    }

    @Override
    public List<PoolItem> list(AccountId accountId, String awemeId) {
        return bakery();
    }

    @Override
    public String nameOf(String qcProductId) {
        return bakery().stream()
                .filter(p -> p.id().value().equals(qcProductId))
                .map(PoolItem::name)
                .findFirst()
                .orElse("未关联");
    }

    private static PoolItem item(String id, String name, String price) {
        return new PoolItem(new QcProductId(id), name, Money.ofYuan(new BigDecimal(price)), null);
    }
}
