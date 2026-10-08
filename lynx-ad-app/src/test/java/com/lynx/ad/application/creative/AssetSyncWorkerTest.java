package com.lynx.ad.application.creative;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.AssetUploadPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.Instant;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssetSyncWorkerTest {
    private final AssetUploadRepository repository=mock(AssetUploadRepository.class);
    private final AssetUploadService files=mock(AssetUploadService.class);
    private final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    private final AssetUploadPort upload=mock(AssetUploadPort.class);
    private final AssetSyncWorker worker=new AssetSyncWorker(repository,files,credentials,upload);
    @BeforeEach void approved() { when(repository.approved(anyString(),anyString())).thenReturn(true); }
    @AfterEach void stop() { worker.stop(); }
    private AssetUploadRepository.Sync sync(String account) { return new AssetUploadRepository.Sync(account,"tenant-a","asset",account,null,"QUEUED",null,null,null,"UNKNOWN",0,Instant.now(),null,null); }
    @Test void expiredCredentialWaitsForAuthorizationWithoutDownloadingOrRefreshing() {
        when(credentials.getUsableCredential(new TenantId("tenant-a"),new AccountId("expired"))).thenThrow(new BizException(1211,"expired"));
        worker.process(sync("expired"));
        verify(repository).fail(any(),eq("WAITING_AUTH"),anyString(),anyInt());
        verifyNoInteractions(files,upload);
    }
    @Test void partialFailureDoesNotUndoStoredFileOrSuccessfulAccount() throws Exception {
        var now=Instant.now();
        var asset=new AssetUploadRepository.Asset("asset","tenant-a","user","request","IMAGE","test.jpg","image/jpeg",4,"object-key","STORED","local",null,null,"md5",false,1,1,null,null,null,now,now);
        when(repository.require("tenant-a","asset")).thenReturn(asset);
        Path file=Path.of("missing-test-file"); when(files.download(any(),eq(asset))).thenReturn(file);
        Credential first=new Credential("token-a",new AdvertiserId("11")), second=new Credential("token-b",new AdvertiserId("22"));
        when(credentials.getUsableCredential(new TenantId("tenant-a"),new AccountId("first"))).thenReturn(first);
        when(credentials.getUsableCredential(new TenantId("tenant-a"),new AccountId("second"))).thenReturn(second);
        when(upload.upload(eq(first),eq(AssetKind.IMAGE),eq(file),anyString(),anyString(),anyString(),eq(false)))
                .thenReturn(new AssetUploadPort.Result("image-id","9223372036854775806","request-id"));
        when(upload.upload(eq(second),eq(AssetKind.IMAGE),eq(file),anyString(),anyString(),anyString(),eq(false))).thenThrow(new BizException(1523,"ambiguous"));
        worker.process(sync("first")); worker.process(sync("second"));
        verify(repository).finish(any(),eq("11"),eq("image-id"),eq("9223372036854775806"),eq("request-id"));
        verify(repository).fail(argThat(s->s.accountId().equals("second")),eq("UNCERTAIN"),anyString(),anyInt());
        verify(repository,never()).storageFailed(any(),anyString());
    }
    @Test void eachAttemptReadsTheCurrentDatabaseCredential() {
        when(credentials.getUsableCredential(any(),any())).thenThrow(new BizException(1211,"expired"));
        worker.process(sync("account")); worker.process(sync("account"));
        verify(credentials,times(2)).getUsableCredential(new TenantId("tenant-a"),new AccountId("account"));
    }
    @Test void authPollingResumesOnlyAfterTheDatabaseCredentialChanges() throws Exception {
        var sync=sync("account");
        var credential=new Credential("rejected-token",new AdvertiserId("11"));
        String fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("rejected-token:11".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(repository.waitingAuth()).thenReturn(java.util.List.of(sync));
        when(repository.attemptedCredential(sync)).thenReturn(fingerprint);
        when(credentials.getUsableCredential(any(),any())).thenReturn(credential);
        worker.resumeAuthorized(); verify(repository,never()).retry(anyString(),anyString(),anyString(),anyBoolean());
        when(credentials.getUsableCredential(any(),any())).thenReturn(new Credential("renewed-token",new AdvertiserId("11")));
        worker.resumeAuthorized(); verify(repository).retry("tenant-a","asset","account",false);
    }
    @Test void unapprovedAiReviewNeverReadsCredentialsOrUploads() {
        when(repository.approved("tenant-a","asset")).thenReturn(false);
        worker.process(sync("account"));
        verify(repository).fail(any(),eq("WAITING_REVIEW"),anyString(),eq(0));
        verifyNoInteractions(files,credentials,upload);
    }
}
