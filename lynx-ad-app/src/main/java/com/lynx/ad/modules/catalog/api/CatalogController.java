package com.lynx.ad.modules.catalog.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.ProductId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.security.RequiresActiveTrial;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.catalog.api.dto.ProductVo;
import com.lynx.ad.modules.catalog.api.dto.SelectProductsRequest;
import com.lynx.ad.modules.catalog.api.dto.SelectProductsResponse;
import com.lynx.ad.modules.catalog.application.ProductCommandService;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

    private final ProductQueryService queryService;
    private final ProductCommandService commandService;

    public CatalogController(ProductQueryService queryService, ProductCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping("/api/v1/catalog/products")
    public ApiResponse<List<ProductVo>> listPool(
            @RequestParam String awemeId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,@RequestParam(defaultValue="false") boolean refreshPrices) {
        return ApiResponse.ok(queryService.pool(TenantContext.requireTenantId(), new AwemeId(awemeId), tag, keyword,refreshPrices));
    }

    @RequiresActiveTrial
    @PostMapping({"/api/v1/catalog/products/select", "/products/select"})
    public ApiResponse<SelectProductsResponse> select(@RequestBody SelectProductsRequest req) {
        List<QcProductId> ids = req.productIds() == null
                ? List.of()
                : req.productIds().stream().map(QcProductId::new).toList();
        return ApiResponse.ok(commandService.select(TenantContext.requireTenantId(), new AwemeId(req.awemeId()), ids));
    }

    @PutMapping("/api/v1/catalog/products/{productId}/price")
    public ApiResponse<ProductVo> changePrice(@PathVariable String productId, @RequestBody ChangePriceRequest req) {
        return ApiResponse.ok(commandService.changePrice(
                TenantContext.requireTenantId(), new ProductId(productId), Money.ofYuan(req.userPrice())));
    }

    public record ChangePriceRequest(BigDecimal userPrice) {}
}
