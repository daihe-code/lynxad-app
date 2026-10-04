package com.lynx.ad.modules.catalog.domain.service;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.catalog.domain.model.ProductSelection;

public class ProductSelectPolicy {

    public static final int MAX = 20;

    public void assertSelectable(ProductSelection selection) {
        int size = selection == null ? 0 : selection.size();
        if (size == 0 || size >= 21) {
            throw new DomainException(1301, size == 0 ? "请至少选择 1 个商品" : "最多选择 20 个商品");
        }
    }
}
