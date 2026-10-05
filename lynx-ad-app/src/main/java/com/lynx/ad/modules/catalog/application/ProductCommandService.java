package com.lynx.ad.modules.catalog.application;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.ProductId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.catalog.api.dto.ProductVo;
import com.lynx.ad.modules.catalog.api.dto.SelectProductsResponse;
import com.lynx.ad.modules.catalog.domain.model.Product;
import com.lynx.ad.modules.catalog.domain.model.ProductSelection;
import com.lynx.ad.modules.catalog.domain.port.ProductPoolPort;
import com.lynx.ad.modules.catalog.domain.port.ProductRepository;
import com.lynx.ad.modules.catalog.domain.service.ProductSelectPolicy;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductCommandService {

    private final ProductRepository repository;
    private final ProductPoolPort productPoolPort;
    private final OauthAccountRepository oauthAccountRepository;
    private final ProductSelectPolicy policy = new ProductSelectPolicy();

    public ProductCommandService(
            ProductRepository repository,
            ProductPoolPort productPoolPort,
            OauthAccountRepository oauthAccountRepository) {
        this.repository = repository;
        this.productPoolPort = productPoolPort;
        this.oauthAccountRepository = oauthAccountRepository;
    }

    public SelectProductsResponse select(TenantId tenantId, AwemeId awemeId, List<QcProductId> ids) {
        policy.assertSelectable(new ProductSelection(awemeId, ids));
        AccountId accountId = oauthAccountRepository.findByTenant(tenantId).stream()
                .findFirst()
                .map(a -> a.id())
                .orElse(new AccountId("unbound"));
        List<ProductPoolPort.PoolItem> pool = productPoolPort.list(accountId, awemeId.value());
        // Selecting again must reuse the same tenant/aweme/product record.
        // The database enforces uniqueness on this combination.
        var existing = new HashMap<QcProductId, Product>();
        repository.findByAweme(tenantId, awemeId).forEach(p -> existing.put(p.qcProductId(), p));
        List<ProductVo> vos = new ArrayList<>();
        for (QcProductId id : ids) {
            Product product = existing.get(id);
            if (product == null) {
                product = pool.stream()
                    .filter(p -> p.id().equals(id))
                    .findFirst()
                    .map(p -> Product.fromPool(tenantId, awemeId, p.id(), p.name(), p.price(), p.imageUrl()))
                    .orElseGet(() -> Product.fromPool(tenantId, awemeId, id, id.value(), Money.ofYuan(9.9), null));
                existing.put(id, product);
            }
            repository.upsert(product);
            vos.add(toVo(product));
        }
        return new SelectProductsResponse(vos, null, vos.size() >= 15 ? "选品较多，建议聚焦爆款" : null);
    }

    public ProductVo changePrice(TenantId tenantId, ProductId id, Money userPrice) {
        Product product = repository.findById(tenantId, id).orElseThrow(() -> new BizException(1302, "商品不存在"));
        product.changeUserPrice(userPrice);
        repository.upsert(product);
        return toVo(product);
    }

    static ProductVo toVo(Product p) {
        return new ProductVo(
                p.id().value(),
                p.qcProductId().value(),
                p.name(),
                p.poolPrice().amount(),
                p.sellingPrice().amount(),
                p.imageUrl());
    }
}
