package com.lynx.ad.modules.catalog.domain.port;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.ProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.catalog.domain.model.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    void upsert(Product product);

    Optional<Product> findById(TenantId tenantId, ProductId id);

    List<Product> findByAweme(TenantId tenantId, AwemeId awemeId);
}
