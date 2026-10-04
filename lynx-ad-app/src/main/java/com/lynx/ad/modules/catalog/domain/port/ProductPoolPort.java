package com.lynx.ad.modules.catalog.domain.port;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Money;
import java.util.List;

public interface ProductPoolPort {

    record PoolItem(QcProductId id, String name, Money price, String imageUrl) {}

    List<PoolItem> list(AccountId accountId, String awemeId);

    default String nameOf(String qcProductId) {
        return "未关联";
    }
}
