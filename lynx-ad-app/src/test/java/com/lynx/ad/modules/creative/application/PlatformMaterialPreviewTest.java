package com.lynx.ad.modules.creative.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformMaterialPreviewTest {
    private final TenantId tenant = new TenantId("tenant");
    private final PlatformMaterialCatalog catalog = mock(PlatformMaterialCatalog.class);
    private final OauthAccountRepository accounts = mock(OauthAccountRepository.class);
    private final QianchuanCredentialFacade credentials = mock(QianchuanCredentialFacade.class);
    private final MaterialLibraryPort library = mock(MaterialLibraryPort.class);
    private final AssetPreviewService service = new AssetPreviewService(mock(AssetUploadRepository.class), accounts, credentials, library);

    @Test void remoteOnlyPreviewUsesOwnedStableIdAndRefreshesExpiredUrl() {
        service.platformCatalog(catalog);
        var record = new PlatformMaterialCatalog.Material("tenant", "account", "advertiser", MaterialType.IMAGE,
                "m:material", "object", "material", "image.jpg", 100, 100, 100, null, "SQUARE", "NONE", Instant.now());
        when(catalog.find("tenant", "account", MaterialType.IMAGE, "m:material")).thenReturn(Optional.of(record));
        var account = mock(OauthAccount.class);
        when(account.platform()).thenReturn(Platform.QIANCHUAN); when(account.status()).thenReturn("ACTIVE");
        when(accounts.findById(tenant, new AccountId("account"))).thenReturn(Optional.of(account));
        when(credentials.getUsableCredential(tenant, new AccountId("account")))
                .thenReturn(new Credential("private-token", new AdvertiserId("advertiser"), "account"));
        when(library.list(any())).thenReturn(new MaterialLibraryPort.MaterialPage(List.of(material("old"))),
                new MaterialLibraryPort.MaterialPage(List.of(material("new"))));
        var id = "EXT:account:IMAGE:m:material";
        assertEquals("https://example.com/old", service.platformPreview(tenant, id, false).posterUrl());
        assertEquals("https://example.com/old", service.platformPreview(tenant, id, false).posterUrl());
        assertEquals("https://example.com/new", service.platformPreview(tenant, id, true).posterUrl());
        verify(library, times(2)).list(any());
        when(library.list(any())).thenReturn(new MaterialLibraryPort.MaterialPage(List.of(
                new MaterialLibraryPort.PlatformMaterial("object", "different-material", "wrong.jpg", 1,
                        100, 100, null, "SQUARE", "EXTERNAL", Instant.now(),
                        "https://example.com/wrong", "", false, "NONE"))));
        assertEquals("UNAVAILABLE", service.platformPreview(tenant, id, true).source());
        assertEquals("UNAVAILABLE", service.platformPreview(new TenantId("other"), id, false).source());
        verify(catalog).find("other", "account", MaterialType.IMAGE, "m:material");
    }

    private static MaterialLibraryPort.PlatformMaterial material(String version) {
        return new MaterialLibraryPort.PlatformMaterial("object", "material", "image.jpg", 100,
                100, 100, null, "SQUARE", "EXTERNAL", Instant.now(),
                "https://example.com/" + version, "", false, "NONE");
    }
}
