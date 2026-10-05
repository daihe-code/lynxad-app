package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.creative.application.LibraryQueryService;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LibraryReviewFilterTest {
    @Test
    void reviewStatesHaveSeparateCountsAndFilterBeforePagination() {
        var tenant = new TenantId("review-filter-tenant");
        var repository = mock(CreativeTaskRepository.class);
        var materials = mock(MaterialLibraryPort.class);
        var oauth = mock(OauthQueryService.class);
        var bindings = mock(PlanCreativeBindingQuery.class);
        when(repository.listPackagesByTenant(tenant)).thenReturn(List.of());
        when(oauth.hasBoundQianchuan(tenant)).thenReturn(true);
        when(bindings.usedKeys(tenant)).thenReturn(Set.of("used", "reviewing"));
        when(materials.list(any())).thenAnswer(invocation -> {
            MaterialLibraryPort.MaterialLibraryQuery query = invocation.getArgument(0);
            return new MaterialLibraryPort.MaterialPage(query.type() == MaterialType.VIDEO
                    ? List.of(material("used", "NONE"), material("idle", "NONE"),
                            material("reviewing", "REVIEWING"), material("rejected", "REJECTED"))
                    : List.of());
        });
        var service = new LibraryQueryService(repository, mock(StoragePort.class), materials,
                oauth, bindings, mock(ProductQueryService.class));
        var from = LocalDate.of(2026, 10, 1);
        var to = LocalDate.of(2026, 10, 7);
        var all = service.query(tenant, "VIDEO", "QIANCHUAN", from, to, "ALL", null, 1, 8);
        assertEquals(4, all.statusCounts().all());
        assertEquals(1, all.statusCounts().used());
        assertEquals(1, all.statusCounts().unused());
        assertEquals(1, all.statusCounts().reviewing());
        assertEquals(1, all.statusCounts().rejected());
        assertEquals(1080, all.items().getFirst().width());
        assertEquals(1920, all.items().getFirst().height());
        for (String status : List.of("USED", "UNUSED", "REVIEWING", "REJECTED")) {
            var result = service.query(tenant, "VIDEO", "QIANCHUAN", from, to, status, null, 1, 1);
            assertEquals(1, result.pageInfo().total());
            assertEquals(status.equals("UNUSED") ? "idle" : status.toLowerCase(), result.items().getFirst().objectId());
        }
        var searched = service.query(tenant, "VIDEO", "QIANCHUAN", from, to, "REJECTED", "used", 1, 8);
        assertEquals(1, searched.statusCounts().all());
        assertEquals(0, searched.pageInfo().total());
        var images = service.query(tenant, "IMAGE", "QIANCHUAN", from, to, "ALL", null, 1, 8);
        assertEquals(0, images.pageInfo().total());
        assertEquals(4, images.tabCounts().video());
    }

    private static MaterialLibraryPort.PlatformMaterial material(String id, String review) {
        return new MaterialLibraryPort.PlatformMaterial(id, id, id + ".mp4", 1024L,
                1080, 1920, 15, "VIDEO_VERTICAL", "EXTERNAL", Instant.parse("2026-10-05T00:00:00Z"),
                "https://example.com/" + id, "", false, review);
    }
}
