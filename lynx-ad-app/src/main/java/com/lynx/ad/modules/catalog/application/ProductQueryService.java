package com.lynx.ad.modules.catalog.application;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.catalog.api.dto.ProductVo;
import com.lynx.ad.modules.catalog.domain.port.ProductPoolPort;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProductQueryService {

    private final ProductPoolPort productPoolPort;
    private final OauthAccountRepository oauthAccountRepository;

    public ProductQueryService(ProductPoolPort productPoolPort, OauthAccountRepository oauthAccountRepository) {
        this.productPoolPort = productPoolPort;
        this.oauthAccountRepository = oauthAccountRepository;
    }

    public List<ProductVo> pool(TenantId tenantId, AwemeId awemeId, String tag, String keyword) {
        AccountId accountId = oauthAccountRepository.findByTenant(tenantId).stream()
                .findFirst()
                .map(a -> a.id())
                .orElse(new AccountId("unbound"));
        return productPoolPort.list(accountId, awemeId.value()).stream()
                .filter(p -> keyword == null || keyword.isBlank() || p.name().contains(keyword))
                .map(p -> new ProductVo(
                        UUID.nameUUIDFromBytes((tenantId.value() + p.id().value()).getBytes()).toString(),
                        p.id().value(),
                        p.name(),
                        p.price().amount(),
                        p.price().amount(),
                        p.imageUrl()))
                .toList();
    }

    public String nameOf(String qcProductId) {
        return productPoolPort.nameOf(qcProductId);
    }
}
