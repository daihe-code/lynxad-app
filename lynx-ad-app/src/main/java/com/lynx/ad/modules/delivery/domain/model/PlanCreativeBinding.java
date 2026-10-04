package com.lynx.ad.modules.delivery.domain.model;

import com.lynx.ad.common.domain.Ids.CoverImageId;
import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.VideoId;
import com.lynx.ad.modules.creative.domain.model.AdTitle;
import java.util.List;

public class PlanCreativeBinding {

    private final PackageId packageId;
    private final QcProductId productId;
    private final VideoId videoId;
    private final CoverImageId coverImageId;
    private final String materialId;
    private final List<AdTitle> titles;

    public PlanCreativeBinding(
            PackageId packageId,
            QcProductId productId,
            VideoId videoId,
            CoverImageId coverImageId,
            List<AdTitle> titles) {
        this(packageId, productId, videoId, coverImageId, null, titles);
    }

    public PlanCreativeBinding(
            PackageId packageId,
            QcProductId productId,
            VideoId videoId,
            CoverImageId coverImageId,
            String materialId,
            List<AdTitle> titles) {
        this.packageId = packageId;
        this.productId = productId;
        this.videoId = videoId;
        this.coverImageId = coverImageId;
        this.materialId = materialId;
        this.titles = titles == null ? List.of() : List.copyOf(titles);
    }

    public PackageId packageId() {
        return packageId;
    }

    public QcProductId productId() {
        return productId;
    }

    public VideoId videoId() {
        return videoId;
    }

    public CoverImageId coverImageId() {
        return coverImageId;
    }

    public String materialId() {
        return materialId;
    }

    public List<AdTitle> titles() {
        return titles;
    }
}
