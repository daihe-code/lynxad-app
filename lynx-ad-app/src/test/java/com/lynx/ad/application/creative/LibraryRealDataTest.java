package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.creative.application.LibraryQueryService;
import com.lynx.ad.modules.creative.domain.model.*;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.MockMaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LibraryRealDataTest {
    private final TenantId tenant = new TenantId("real-library");
    private final Instant created = Instant.parse("2026-10-07T00:00:00Z");

    @Test
    void mockGenerationDoesNotHideOlderRealMaterialOrCountAsLibraryContent() {
        var packages = mock(CreativeTaskRepository.class);
        var real = artifact("product", false);
        when(packages.listPackagesByTenant(tenant)).thenReturn(List.of(
                new CreativePackage(new PackageId("real"), 1, null, List.of(real), created),
                new CreativePackage(new PackageId("mock"), 2, null,
                        List.of(artifact("product", true), artifact("mock-only", true)), created)));
        var service = service(packages);
        var result = query(service, "VIDEO");
        assertEquals(1, result.tabCounts().video());
        assertEquals(1, result.pageInfo().total());
        assertEquals("LYNX:real:product", result.items().getFirst().id());
        assertEquals("real-product", service.libraryVideos(tenant).getFirst().videoId());
    }

    @Test
    void realUploadsOfEveryKindRemainVisibleWithEmptyMockPlatform() {
        var packages = mock(CreativeTaskRepository.class);
        when(packages.listPackagesByTenant(tenant)).thenReturn(List.of());
        var uploads = mock(AssetUploadRepository.class);
        var kinds = List.of("VIDEO", "IMAGE", "AUDIO", "TEXT");
        when(uploads.list(tenant.value())).thenReturn(kinds.stream().map(kind ->
                new AssetUploadRepository.Asset(kind, tenant.value(), "user", kind, kind,
                        "real-" + kind, "application/octet-stream", 1024, "key-" + kind,
                        "STORED", "oss", null, null, "md5", false, null, null, null,
                        null, null, created, created)).toList());
        for (String kind : kinds) when(uploads.syncs(tenant.value(), kind)).thenReturn(List.of());
        var service = service(packages);
        service.uploadDependencies(uploads, null, null);
        for (String kind : kinds) {
            var result = query(service, kind);
            assertEquals(1, result.pageInfo().total());
            assertEquals("UPLOAD:" + kind, result.items().getFirst().id());
        }
        assertEquals(0, query(service, "CAROUSEL").pageInfo().total());
        for (MaterialType type : MaterialType.values()) assertTrue(new MockMaterialLibraryPort()
                .list(new MaterialLibraryPort.MaterialLibraryQuery("account", type,
                        LocalDate.of(1970, 1, 1), LocalDate.of(2099, 12, 31), 1, 20, null)).items().isEmpty());
    }

    private CreativeArtifact artifact(String product, boolean mock) {
        return new CreativeArtifact(new QcProductId(product), List.of(),
                Optional.of(new VideoId("real-" + product)), Optional.empty(), "key-" + product,
                null, "local", product + ".mp4", 1024L, 12, 720, 1280,
                "VIDEO_VERTICAL", created, mock);
    }

    private LibraryQueryService service(CreativeTaskRepository packages) {
        var oauth = mock(OauthQueryService.class);
        when(oauth.hasBoundQianchuan(tenant)).thenReturn(true);
        var bindings = mock(PlanCreativeBindingQuery.class);
        when(bindings.usedKeys(tenant)).thenReturn(Set.of());
        return new LibraryQueryService(packages, mock(StoragePort.class), new MockMaterialLibraryPort(),
                oauth, bindings, mock(ProductQueryService.class));
    }

    private com.lynx.ad.modules.creative.api.dto.LibraryResponse query(LibraryQueryService service, String kind) {
        return service.query(tenant, kind, "QIANCHUAN", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 7), "ALL", null, 1, 8);
    }
}
