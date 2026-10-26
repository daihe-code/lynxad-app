package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.creative.application.LibraryQueryService;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LibraryOriginalTimeTest {
    @Test void apiPreservesOriginalDatesSortsUnknownLastAndFiltersByUploadDate() {
        var tenant = new TenantId("tenant");
        var oauth = mock(OauthQueryService.class); when(oauth.hasBoundQianchuan(tenant)).thenReturn(true);
        var bindings = mock(PlanCreativeBindingQuery.class); when(bindings.usedKeys(tenant)).thenReturn(Set.of());
        var catalog = mock(PlatformMaterialCatalog.class);
        when(catalog.list(eq("tenant"), any(Instant.class), any(Instant.class))).thenReturn(List.of(
                material("unknown", null), material("old", Instant.parse("2026-10-19T12:31:36Z")),
                material("new", Instant.parse("2026-10-21T04:47:58Z"))));
        var service = new LibraryQueryService(mock(CreativeTaskRepository.class), mock(StoragePort.class),
                mock(MaterialLibraryPort.class), oauth, bindings, mock(ProductQueryService.class));
        service.catalogDependency(catalog);
        var rows = service.query(tenant, "VIDEO", "QIANCHUAN", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 26), "ALL", null, 1, 8).items();
        assertEquals(List.of("new", "old", "unknown"), rows.stream().map(row -> row.objectId()).toList());
        assertEquals("2026-10-19T12:31:36Z", rows.get(1).createdAt());
        assertNull(rows.getLast().createdAt());
        var today = service.query(tenant, "VIDEO", "QIANCHUAN", LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 26), "ALL", null, 1, 8).items();
        assertEquals(List.of("unknown"), today.stream().map(row -> row.objectId()).toList());
    }

    private PlatformMaterialCatalog.Material material(String id, Instant created) {
        return new PlatformMaterialCatalog.Material("tenant", "account", "advertiser", MaterialType.VIDEO,
                "m:" + id, id, id, id + ".mp4", 1024, 720, 1280, 10, "VIDEO_VERTICAL", "NONE", created);
    }
}
