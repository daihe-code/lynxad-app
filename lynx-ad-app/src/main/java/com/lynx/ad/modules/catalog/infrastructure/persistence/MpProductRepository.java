package com.lynx.ad.modules.catalog.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.ProductId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.catalog.domain.model.Product;
import com.lynx.ad.modules.catalog.domain.port.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@TableName("product")
class ProductPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String awemeId;
    private String qcProductId;
    private String name;
    private BigDecimal price;
    private BigDecimal userPrice;
    private String imageUrl;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAwemeId() { return awemeId; }
    public void setAwemeId(String awemeId) { this.awemeId = awemeId; }
    public String getQcProductId() { return qcProductId; }
    public void setQcProductId(String qcProductId) { this.qcProductId = qcProductId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getUserPrice() { return userPrice; }
    public void setUserPrice(BigDecimal userPrice) { this.userPrice = userPrice; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

@Mapper
interface ProductMapper extends BaseMapper<ProductPo> {}

@Repository
public class MpProductRepository implements ProductRepository {

    private final ProductMapper mapper;

    public MpProductRepository(ProductMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(Product product) {
        ProductPo po = mapper.selectById(product.id().value());
        boolean insert = po == null;
        if (insert) {
            po = new ProductPo();
            po.setId(product.id().value());
        }
        po.setTenantId(product.tenantId().value());
        po.setAwemeId(product.awemeId().value());
        po.setQcProductId(product.qcProductId().value());
        po.setName(product.name());
        po.setPrice(product.poolPrice().amount());
        po.setUserPrice(product.sellingPrice().amount());
        po.setImageUrl(product.imageUrl());
        po.setUpdatedAt(Instant.now());
        if (insert) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<Product> findById(TenantId tenantId, ProductId id) {
        ProductPo po = mapper.selectById(id.value());
        if (po == null || !tenantId.value().equals(po.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public List<Product> findByAweme(TenantId tenantId, AwemeId awemeId) {
        return mapper.selectList(new LambdaQueryWrapper<ProductPo>()
                        .eq(ProductPo::getTenantId, tenantId.value())
                        .eq(ProductPo::getAwemeId, awemeId.value()))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private Product toDomain(ProductPo po) {
        return new Product(
                new ProductId(po.getId()),
                new TenantId(po.getTenantId()),
                new AwemeId(po.getAwemeId()),
                new QcProductId(po.getQcProductId()),
                po.getName(),
                Money.ofYuan(po.getPrice() == null ? BigDecimal.ZERO : po.getPrice()),
                Money.ofYuan(po.getUserPrice() == null ? po.getPrice() : po.getUserPrice()),
                po.getImageUrl(),
                null);
    }
}
