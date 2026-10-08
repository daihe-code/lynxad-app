package com.lynx.ad.modules.creative.domain.model;

import com.lynx.ad.common.domain.Ids.PackageId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.exception.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreativePackage {

    private final PackageId id;
    private int version;
    private Instant confirmedAt;
    private final Instant createdAt;
    private final List<CreativeArtifact> items;

    public CreativePackage(PackageId id, int version, Instant confirmedAt, List<CreativeArtifact> items) {
        this(id, version, confirmedAt, items, Instant.now());
    }

    public CreativePackage(PackageId id, int version, Instant confirmedAt, List<CreativeArtifact> items, Instant createdAt) {
        this.id = id;
        this.version = version;
        this.confirmedAt = confirmedAt;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.items = new ArrayList<>(items == null ? List.of() : items);
    }

    public static CreativePackage of(List<CreativeArtifact> items) {
        CreativePackage pkg = new CreativePackage(new PackageId(UUID.randomUUID().toString()), 1, null, items);
        pkg.assertTitlesLimit();
        return pkg;
    }

    public void confirm() {
        assertTitlesLimit();
        if (items.isEmpty() || items.stream().anyMatch(item -> !hasMedia(item))) {
            throw new DomainException(1503, "请为所有投放商品补齐素材后再确认");
        }
        if (confirmedAt == null) {
            this.confirmedAt = Instant.now();
            this.version++;
        }
    }

    public void replaceItem(QcProductId productId, CreativeArtifact artifact) {
        items.removeIf(it -> it.productId().equals(productId));
        items.add(artifact);
        this.confirmedAt = null;
        this.version++;
        assertTitlesLimit();
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public CreativeArtifact requireMedia(QcProductId productId, MaterialType type) {
        if (type == null || type == MaterialType.CAROUSEL) throw new DomainException(1501, "当前支持视频和图片素材操作");
        CreativeArtifact item = items.stream().filter(it -> it.productId().equals(productId)).findFirst()
                .orElseThrow(() -> new DomainException(1504, "商品素材不存在"));
        if (type == MaterialType.VIDEO ? item.videoObjectKey() == null && item.videoId().isEmpty()
                : item.coverObjectKey() == null && item.coverImageId().isEmpty()) {
            throw new DomainException(1504, "该类型素材不存在");
        }
        return item;
    }

    public void changeMedia(QcProductId productId, MaterialType type, CreativeArtifact generated) {
        CreativeArtifact old = requireMedia(productId, type);
        boolean video = type == MaterialType.VIDEO;
        CreativeArtifact next = new CreativeArtifact(old.productId(), old.titles(),
                video ? generated == null ? java.util.Optional.empty() : generated.videoId() : old.videoId(),
                video ? old.coverImageId() : generated == null ? java.util.Optional.empty() : generated.coverImageId(),
                video ? generated == null ? null : generated.videoObjectKey() : old.videoObjectKey(),
                video ? old.coverObjectKey() : generated == null ? null : generated.coverObjectKey(),
                generated == null ? old.storageMode() : generated.storageMode(),
                video && generated != null ? generated.filename() : old.filename(),
                video && generated != null ? generated.sizeBytes() : old.sizeBytes(),
                video && generated != null ? generated.durationSec() : old.durationSec(),
                video && generated != null ? generated.width() : old.width(),
                video && generated != null ? generated.height() : old.height(),
                video && generated != null ? generated.imageMode() : old.imageMode(),
                generated == null ? old.createdAt() : generated.createdAt(),
                video && generated != null ? generated.mockContent() : old.mockContent());
        replaceItem(productId, next);
    }

    private boolean hasMedia(CreativeArtifact item) {
        return item.videoObjectKey() != null || item.coverObjectKey() != null
                || item.videoId().isPresent() || item.coverImageId().isPresent();
    }

    public void assertTitlesLimit() {
        for (CreativeArtifact item : items) {
            int n = item.titles() == null ? 0 : item.titles().size();
            if (n < 1 || n > 10) {
                throw new DomainException(1503, "每个商品标题须 1–10 条");
            }
        }
    }

    public PackageId id() {
        return id;
    }

    public int version() {
        return version;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<CreativeArtifact> items() {
        return List.copyOf(items);
    }
}
