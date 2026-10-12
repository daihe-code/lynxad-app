package com.lynx.ad.modules.catalog.domain.port;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Money;
import java.util.List;

public interface ProductPoolPort {

    record PoolItem(QcProductId id, String name, Money price, String imageUrl,
                    com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice priceInfo) {
        public PoolItem(QcProductId id,String name,Money price,String imageUrl) { this(id,name,price,imageUrl,null); }
    }

    List<PoolItem> list(AccountId accountId, String awemeId);
    default List<PoolItem> list(AccountId accountId,String awemeId,boolean refreshPrices) { return list(accountId,awemeId); }

    default String nameOf(String qcProductId) {
        return "未关联";
    }
}
