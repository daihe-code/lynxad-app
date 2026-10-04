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
        this.confirmedAt = Instant.now();
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
