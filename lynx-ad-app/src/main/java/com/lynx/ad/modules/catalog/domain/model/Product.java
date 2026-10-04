package com.lynx.ad.modules.catalog.domain.model;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.ProductId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import java.util.Optional;
import java.util.UUID;

public class Product {

    private final ProductId id;
    private final TenantId tenantId;
    private final AwemeId awemeId;
    private final QcProductId qcProductId;
    private final String name;
    private final Money poolPrice;
    private Money userPrice;
    private final String imageUrl;
    private ProductAnalysis analysis;

    public Product(
            ProductId id,
            TenantId tenantId,
            AwemeId awemeId,
            QcProductId qcProductId,
            String name,
            Money poolPrice,
            Money userPrice,
            String imageUrl,
            ProductAnalysis analysis) {
        this.id = id;
        this.tenantId = tenantId;
        this.awemeId = awemeId;
        this.qcProductId = qcProductId;
        this.name = name;
        this.poolPrice = poolPrice;
        this.userPrice = userPrice;
        this.imageUrl = imageUrl;
        this.analysis = analysis;
    }

    public static Product fromPool(
            TenantId tenantId, AwemeId awemeId, QcProductId qcId, String name, Money poolPrice, String imageUrl) {
        return new Product(
                new ProductId(UUID.randomUUID().toString()),
                tenantId,
                awemeId,
                qcId,
                name,
                poolPrice,
                poolPrice,
                imageUrl,
                null);
    }

    public void changeUserPrice(Money price) {
        this.userPrice = price;
    }

    public void attachAnalysis(ProductAnalysis analysis) {
        this.analysis = analysis;
    }

    public Money sellingPrice() {
        return userPrice != null ? userPrice : poolPrice;
    }

    public ProductId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public AwemeId awemeId() {
        return awemeId;
    }

    public QcProductId qcProductId() {
        return qcProductId;
    }

    public String name() {
        return name;
    }

    public Money poolPrice() {
        return poolPrice;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public Optional<ProductAnalysis> analysis() {
        return Optional.ofNullable(analysis);
    }
}
