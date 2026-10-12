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
    private com.lynx.ad.modules.catalog.domain.port.ProductRepository products;
    @org.springframework.beans.factory.annotation.Autowired
    public void productRepository(com.lynx.ad.modules.catalog.domain.port.ProductRepository value) { products=value; }

    private final ProductPoolPort productPoolPort;
    private final OauthAccountRepository oauthAccountRepository;

    public ProductQueryService(ProductPoolPort productPoolPort, OauthAccountRepository oauthAccountRepository) {
        this.productPoolPort = productPoolPort;
        this.oauthAccountRepository = oauthAccountRepository;
    }

    public List<ProductVo> pool(TenantId tenantId, AwemeId awemeId, String tag, String keyword) {
        return pool(tenantId,awemeId,tag,keyword,false);
    }
    public List<ProductVo> pool(TenantId tenantId, AwemeId awemeId, String tag, String keyword,boolean refreshPrices) {
        AccountId accountId = oauthAccountRepository.findByTenant(tenantId).stream()
                .filter(a -> a.platform()==com.lynx.ad.modules.identity.domain.model.Platform.QIANCHUAN && "ACTIVE".equals(a.status()))
                .filter(a -> a.awemes().stream().anyMatch(w -> w.awemeId().equals(awemeId)))
                .findFirst()
                .map(a -> a.id())
                .orElse(new AccountId("unbound"));
        var saved=new java.util.HashMap<String,com.lynx.ad.modules.catalog.domain.model.Product>();
        if(products!=null)products.findByAweme(tenantId,awemeId).forEach(p->saved.put(p.qcProductId().value(),p));
        var pool=refreshPrices?productPoolPort.list(accountId,awemeId.value(),true):productPoolPort.list(accountId,awemeId.value());
        return pool.stream()
                .filter(p -> keyword == null || keyword.isBlank() || p.name().contains(keyword))
                .map(p -> new ProductVo(
                        saved.containsKey(p.id().value())?saved.get(p.id().value()).id().value():UUID.nameUUIDFromBytes((tenantId.value()+awemeId.value()+p.id().value()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                        p.id().value(),
                        p.name(),
                        p.price()==null?null:p.price().amount(),
                        saved.containsKey(p.id().value()) && saved.get(p.id().value()).userPrice()!=null?saved.get(p.id().value()).userPrice().amount():null,
                        p.imageUrl(),p.priceInfo()))
                .toList();
    }

    public String nameOf(String qcProductId) {
        return productPoolPort.nameOf(qcProductId);
    }
}
