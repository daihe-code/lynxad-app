package com.lynx.ad.modules.creative.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformMaterialSyncWorkerTest {
    private final PlatformMaterialCatalog catalog = mock(PlatformMaterialCatalog.class);
    private final MaterialLibraryPort library = mock(MaterialLibraryPort.class);
    private final QianchuanCredentialFacade credentials = mock(QianchuanCredentialFacade.class);
    private final PlatformMaterialSyncWorker worker = new PlatformMaterialSyncWorker(catalog, library, credentials);
    private final PlatformMaterialCatalog.Account account = new PlatformMaterialCatalog.Account("tenant", "account");

    @Test void paginatesWholeCatalogAndNeverPersistsPreviewLinks() {
        when(credentials.getUsableCredential(new TenantId("tenant"), new AccountId("account")))
                .thenReturn(new Credential("secret", new AdvertiserId("advertiser"), "account"));
        when(library.list(any())).thenAnswer(call -> {
            MaterialLibraryPort.MaterialLibraryQuery q = call.getArgument(0);
            if (q.type() != MaterialType.IMAGE) return new MaterialLibraryPort.MaterialPage(List.of());
            var result = new ArrayList<MaterialLibraryPort.PlatformMaterial>();
            int count = q.page() == 1 ? 20 : 1;
            for (int i = 0; i < count; i++) result.add(material("image-" + ((q.page() - 1) * 20 + i)));
            return new MaterialLibraryPort.MaterialPage(result);
        });
        worker.sync(account);
        var pages = ArgumentCaptor.forClass(MaterialLibraryPort.MaterialLibraryQuery.class);
        verify(library, times(4)).list(pages.capture());
        assertEquals(List.of(1, 2), pages.getAllValues().stream().filter(q -> q.type() == MaterialType.IMAGE)
                .map(MaterialLibraryPort.MaterialLibraryQuery::page).toList());
        var persisted = ArgumentCaptor.forClass(java.util.Map.class);
        verify(catalog).replaceAll(eq("tenant"), eq("account"), eq("advertiser"), persisted.capture());
        var images = (List<MaterialLibraryPort.PlatformMaterial>) persisted.getValue().get(MaterialType.IMAGE);
        assertEquals(21, images.size());
        assertEquals("image-20", images.getLast().objectId());
    }

    @Test void failedLaterPageNeverReplacesIncompleteType() {
        when(credentials.getUsableCredential(new TenantId("tenant"), new AccountId("account")))
                .thenReturn(new Credential("secret", new AdvertiserId("advertiser"), "account"));
        when(library.list(any())).thenAnswer(call -> {
            MaterialLibraryPort.MaterialLibraryQuery q = call.getArgument(0);
            if (q.type() != MaterialType.IMAGE) return new MaterialLibraryPort.MaterialPage(List.of());
            if (q.page() == 2) throw new IllegalStateException("provider failed");
            return new MaterialLibraryPort.MaterialPage(java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> material("image-" + i)).toList());
        });
        assertThrows(IllegalStateException.class, () -> worker.sync(account));
        verify(catalog, never()).replaceAll(eq("tenant"), eq("account"), eq("advertiser"), any());
    }

    private static MaterialLibraryPort.PlatformMaterial material(String id) {
        return new MaterialLibraryPort.PlatformMaterial(id, id, id + ".jpg", 10, 100, 100, null,
                "SQUARE", "EXTERNAL", Instant.now(), "https://signed.example/temporary", "", false, "NONE");
    }
}
