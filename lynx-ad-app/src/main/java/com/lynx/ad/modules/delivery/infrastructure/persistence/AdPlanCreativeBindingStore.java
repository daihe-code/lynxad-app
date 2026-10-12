package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lynx.ad.common.domain.Ids.PlanId;
import com.lynx.ad.modules.delivery.domain.model.PlanCreativeBinding;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
public class AdPlanCreativeBindingStore {

    private final AdPlanCreativeMapper mapper;

    public AdPlanCreativeBindingStore(AdPlanCreativeMapper mapper) {
        this.mapper = mapper;
    }

    public void upsert(PlanId planId, List<PlanCreativeBinding> bindings) {
        if (planId == null || bindings == null) {
            return;
        }
        mapper.delete(new LambdaQueryWrapper<AdPlanCreativePo>().eq(AdPlanCreativePo::getAdPlanId, planId.value()));
        for (PlanCreativeBinding b : bindings) {
            AdPlanCreativePo po = new AdPlanCreativePo();
            po.setAdPlanId(planId.value());
            po.setPackageId(b.packageId() == null ? "" : b.packageId().value());
            po.setProductId(b.productId().value());
            po.setVideoId(b.videoId() == null ? null : b.videoId().value());
            po.setCoverImageId(b.coverImageId() == null ? null : b.coverImageId().value());
            po.setMaterialId(b.materialId());
            mapper.insert(po);
        }
    }

    public Set<String> usedKeys(List<String> planIds) {
        Set<String> keys = new HashSet<>();
        if (planIds == null || planIds.isEmpty()) {
            return keys;
        }
        for (AdPlanCreativePo po : mapper.selectList(
                new LambdaQueryWrapper<AdPlanCreativePo>().in(AdPlanCreativePo::getAdPlanId, planIds))) {
            if (po.getVideoId() != null && !po.getVideoId().isBlank()) {
                keys.add(po.getVideoId());
            }
            if (po.getCoverImageId() != null && !po.getCoverImageId().isBlank()) {
                keys.add(po.getCoverImageId());
            }
            if (po.getMaterialId() != null && !po.getMaterialId().isBlank()) {
                keys.add(po.getMaterialId());
            }
        }
        return keys;
    }
    public void removeProduct(PlanId planId,String productId) {
        mapper.delete(new LambdaQueryWrapper<AdPlanCreativePo>().eq(AdPlanCreativePo::getAdPlanId,planId.value()).eq(AdPlanCreativePo::getProductId,productId));
    }

    public Map<String, List<String>> productIdsByPlan(List<String> planIds) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (planIds.isEmpty()) return result;
        for (AdPlanCreativePo row : mapper.selectList(
                new LambdaQueryWrapper<AdPlanCreativePo>().in(AdPlanCreativePo::getAdPlanId, planIds))) {
            List<String> ids = result.computeIfAbsent(row.getAdPlanId(), key -> new ArrayList<>());
            if (row.getProductId() != null && !ids.contains(row.getProductId())) ids.add(row.getProductId());
        }
        return result;
    }
}

@TableName("ad_plan_creative")
class AdPlanCreativePo {
    @TableId(type = IdType.INPUT)
    private String adPlanId;
    private String packageId;
    private String productId;
    private String videoId;
    private String coverImageId;
    private String materialId;

    public String getAdPlanId() {
        return adPlanId;
    }

    public void setAdPlanId(String adPlanId) {
        this.adPlanId = adPlanId;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getCoverImageId() {
        return coverImageId;
    }

    public void setCoverImageId(String coverImageId) {
        this.coverImageId = coverImageId;
    }

    public String getMaterialId() {
        return materialId;
    }

    public void setMaterialId(String materialId) {
        this.materialId = materialId;
    }
}

@Mapper
interface AdPlanCreativeMapper extends BaseMapper<AdPlanCreativePo> {}
