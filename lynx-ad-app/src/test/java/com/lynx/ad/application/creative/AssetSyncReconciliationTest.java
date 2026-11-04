package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.domain.port.AssetReceiptLookupPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AssetSyncReconciliationTest {
    static final String HASH=AssetReceiptLookupTest.HASH;
    static class Time extends Clock {
        Instant now=Instant.now();public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}void advance(long seconds){now=now.plusSeconds(seconds);}
    }
    JdbcTemplate jdbc;AssetUploadRepository repository;AssetSyncVerificationStore first,second;Time time;
    final TenantId tenant=new TenantId("tenant");AssetUploadService files;OauthAccountRepository accounts;QianchuanCredentialFacade credentials;AssetReceiptLookupPort lookup;AssetSyncReconciliationService service;
    void database(String mode)throws Exception {
        time=new Time();var ds=new DriverManagerDataSource("jdbc:h2:mem:asset_verify_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000","sa","");
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
        if(mode.equals("PostgreSQL"))jdbc.execute("CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        try(var c=ds.getConnection()){for(String file:List.of("V12__creative_asset_upload.sql","V13__asset_sync_credential_version.sql","V14__asset_ai_review.sql","V19__engine_asset_reference.sql","V39__asset_upload_reconciliation.sql")) {
            if(file.endsWith("__asset_upload_reconciliation.sql"))for(String old:List.of("文件读取或同步任务失败，请重试","千川拒绝上传或文件不符合要求，请检查格式及素材上传权限后重试（错误码 9999）","千川拒绝上传或文件不符合要求，请检查格式及素材上传权限后重试（错误码 1511）")) {
                String key=old.equals("文件读取或同步任务失败，请重试")?"historical":old.contains("9999")?"old-parse":"old-no-code";
                jdbc.update("INSERT INTO creative_asset_platform(id,tenant_id,asset_id,account_id,status,attempts,error_message,next_attempt_at,created_at,updated_at) VALUES(?,'legacy',?,'a','FAILED',1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",key,key.equals("historical")?"old":key,old);
            }
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/"+file));
        }}
        repository=new AssetUploadRepository(jdbc);first=new AssetSyncVerificationStore(jdbc,manager,time);second=new AssetSyncVerificationStore(new JdbcTemplate(ds),manager,time);
        files=mock(AssetUploadService.class);accounts=mock(OauthAccountRepository.class);credentials=mock(QianchuanCredentialFacade.class);lookup=mock(AssetReceiptLookupPort.class);
        var bound=mock(OauthAccount.class);when(bound.platform()).thenReturn(Platform.QIANCHUAN);when(bound.status()).thenReturn("ACTIVE");when(accounts.findById(tenant,new AccountId("account"))).thenReturn(Optional.of(bound));
        when(credentials.getUsableCredential(tenant,new AccountId("account"))).thenReturn(new Credential("test-token",new AdvertiserId("123")));
        service=new AssetSyncReconciliationService(repository,first,files,accounts,credentials,lookup);insert("tenant","asset","account");
    }
    void insert(String owner,String id,String account) {
        var now=Instant.now();var asset=new AssetUploadRepository.Asset(id,owner,"user",id,"IMAGE","image.jpg","image/jpeg",42,"key-"+id,"STORED","local",null,null,HASH,true,800,800,null,null,null,now,now);
        repository.insert(asset);jdbc.update("UPDATE creative_asset SET md5=?,width=800,height=800 WHERE id=?",HASH,id);repository.addSync(asset,account);
        jdbc.update("UPDATE creative_asset_platform SET status='UNCERTAIN',attempts=1,attempted_signature=?,attempted_advertiser_id='123',upload_dispatched_at=CURRENT_TIMESTAMP,upload_evidence_version=1,request_id='original-request' WHERE asset_id=?",HASH,id);
    }
    AssetUploadRepository.Sync sync(){return repository.syncs("tenant","asset").getFirst();}
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void migrationQuarantinesHistoricalCatchAllFailuresInsteadOfMakingThemRetryable(String mode)throws Exception {
        database(mode);var row=repository.syncs("legacy","old").getFirst();assertEquals("UNCERTAIN",row.status());assertTrue(row.errorMessage().contains("历史"));assertEquals(0,repository.retry("legacy","old","a",false));assertEquals(0,jdbc.queryForObject("SELECT upload_evidence_version FROM creative_asset_platform WHERE id='historical'",Integer.class));
        assertEquals("UNCERTAIN",repository.syncs("legacy","old-parse").getFirst().status());assertEquals("UNCERTAIN",repository.syncs("legacy","old-no-code").getFirst().status());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void exactReceiptRepairsOnlyItsOriginalTargetWithoutReupload(String mode)throws Exception {
        database(mode);insert("other","foreign","account");
        var c=first.claim(sync()).orElseThrow();assertTrue(second.claim(sync()).isEmpty());
        assertTrue(first.finish(c,new AssetReceiptLookupPort.Result("CONFIRMED","file-id","9223372036854775806")));
        var saved=sync();assertEquals("UPLOADED",saved.status());assertEquals("123",saved.advertiserId());assertEquals("file-id",saved.objectId());assertEquals("9223372036854775806",saved.materialId());assertEquals("original-request",saved.requestId());assertEquals("UNKNOWN",saved.reviewStatus());assertEquals(1,saved.attempts());
        assertEquals("CONFIRMED",saved.verification().state());assertEquals("UNCERTAIN",repository.syncs("other","foreign").getFirst().status());assertFalse(first.finish(c,AssetReceiptLookupPort.Result.pending("NOT_FOUND")));
        assertEquals(0,repository.retry("tenant","asset",null,false));verifyNoInteractions(files,credentials,lookup);
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void missingAmbiguousOrAuthResultsKeepUncertaintyAndNeverQueue(String mode)throws Exception {
        database(mode);
        for(String state:List.of("NOT_FOUND","AMBIGUOUS","INCOMPLETE","AUTH_REQUIRED","UNAVAILABLE")) {
            var claim=first.claim(sync()).orElseThrow();assertTrue(first.finish(claim,AssetReceiptLookupPort.Result.pending(state)));
            assertEquals("UNCERTAIN",sync().status());assertNull(sync().objectId());assertEquals(1,sync().attempts());assertTrue(second.claim(sync()).isEmpty());
            assertEquals(0,repository.retry("tenant","asset","account",false));assertEquals(1523,assertThrows(BizException.class,()->repository.retry("tenant","asset","account",true)).code());time.advance(31);
        }
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void expiredVerificationOwnerCannotOverwriteNewOwnerOrAttempt(String mode)throws Exception {
        database(mode);var stale=first.claim(sync()).orElseThrow();time.advance(121);var fresh=second.claim(sync()).orElseThrow();
        assertFalse(first.finish(stale,new AssetReceiptLookupPort.Result("CONFIRMED","old","11")));
        assertTrue(second.finish(fresh,AssetReceiptLookupPort.Result.pending("NOT_FOUND")));assertEquals("UNCERTAIN",sync().status());
        time.advance(31);var next=first.claim(sync()).orElseThrow();jdbc.update("UPDATE creative_asset_platform SET attempts=2 WHERE asset_id='asset'");
        assertFalse(first.finish(next,new AssetReceiptLookupPort.Result("CONFIRMED","old","11")));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void independentApiInstancesShareOneVerificationClaim(String mode)throws Exception {
        database(mode);var s=sync();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {var a=pool.submit(()->{gate.await();return first.claim(s);});var b=pool.submit(()->{gate.await();return second.claim(s);});gate.countDown();assertEquals(1,(a.get().isPresent()?1:0)+(b.get().isPresent()?1:0));}finally{pool.shutdownNow();}
        var foreign=new AssetUploadRepository.Sync(s.id(),"other",s.assetId(),s.accountId(),null,"UNCERTAIN",null,null,null,"UNKNOWN",1,null,null,null);assertTrue(first.claim(foreign).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void onlyProvenUnsentNewAttemptsCanResumeAndStaleWorkersCannotSend(String mode)throws Exception {
        database(mode);insert("tenant","sent","account");insert("tenant","legacy","account");
        jdbc.update("UPDATE creative_asset_platform SET status='RUNNING',lease_until=?",Timestamp.from(Instant.now().minusSeconds(100)));
        jdbc.update("UPDATE creative_asset_platform SET upload_dispatched_at=NULL WHERE asset_id='asset'");jdbc.update("UPDATE creative_asset_platform SET upload_evidence_version=0,upload_dispatched_at=NULL WHERE asset_id='legacy'");
        var old=sync();repository.recoverExpired();assertEquals("QUEUED",sync().status());assertEquals("UNCERTAIN",repository.syncs("tenant","sent").getFirst().status());assertEquals("UNCERTAIN",repository.syncs("tenant","legacy").getFirst().status());
        assertFalse(repository.dispatched(old,"123",HASH));
        // Approve file and start a genuinely new attempt; marker is single-use.
        jdbc.update("INSERT INTO creative_asset_review(asset_id,tenant_id,status,next_attempt_at,created_at,updated_at) VALUES('asset','tenant','APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        var queued=sync();assertTrue(repository.claim(queued));assertTrue(repository.dispatched(queued,"123",HASH));assertFalse(repository.dispatched(queued,"123",HASH));assertFalse(repository.claim(queued));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void serviceRequiresOriginalScopeAndEvidenceBeforeReadingPlatform(String mode)throws Exception {
        database(mode);jdbc.update("UPDATE creative_asset_platform SET attempted_signature=NULL WHERE asset_id='asset'");
        assertEquals("MISSING_EVIDENCE",service.reconcile(tenant,"asset","account").state());verifyNoInteractions(credentials,lookup);
        time.advance(31);jdbc.update("UPDATE creative_asset_platform SET attempted_signature=? WHERE asset_id='asset'",HASH);
        when(credentials.getUsableCredential(tenant,new AccountId("account"))).thenReturn(new Credential("test-token",new AdvertiserId("456")));
        assertEquals("SCOPE_CHANGED",service.reconcile(tenant,"asset","account").state());verifyNoInteractions(lookup);assertEquals("UNCERTAIN",sync().status());
        assertThrows(BizException.class,()->service.reconcile(tenant,"asset","foreign-account"));assertThrows(BizException.class,()->service.reconcile(new TenantId("other"),"asset","account"));
        verify(files,never()).download(any(),any());verify(files,never()).retry(any(),any(),any(),anyBoolean());verify(files,never()).ensureTarget(any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void serviceConfirmsWithOneLookupAndDoesNotResumeAuthFailures(String mode)throws Exception {
        database(mode);when(lookup.find(any(),any())).thenReturn(new AssetReceiptLookupPort.Result("CONFIRMED","one","11"));
        assertEquals("CONFIRMED",service.reconcile(tenant,"asset","account").state());verify(lookup).find(any(),argThat(e->HASH.equals(e.signature())&&e.aigc()&&e.sizeBytes()==42));
        service.reconcile(tenant,"asset","account");verifyNoMoreInteractions(lookup);verify(files,never()).download(any(),any());verify(files,never()).retry(any(),any(),any(),anyBoolean());
        jdbc.update("UPDATE creative_asset_platform SET status='UNCERTAIN',verification_checked_at=NULL WHERE asset_id='asset'");when(credentials.getUsableCredential(any(),any())).thenThrow(new BizException(1211,"private upstream message"));
        assertEquals("AUTH_REQUIRED",service.reconcile(tenant,"asset","account").state());assertEquals("UNCERTAIN",sync().status());assertFalse(sync().verification().message().contains("private"));
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void receiptWriteFailureSurvivesRestartAsUncertainAndCanBeRepairedWithoutSecondUpload(String mode)throws Exception {
        database(mode);jdbc.update("UPDATE creative_asset_platform SET status='QUEUED',attempts=0 WHERE asset_id='asset'");
        jdbc.update("INSERT INTO creative_asset_review(asset_id,tenant_id,status,next_attempt_at,created_at,updated_at) VALUES('asset','tenant','APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        var queued=sync();assertTrue(repository.claim(queued));var failing=spy(repository);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("local receipt commit failed")).when(failing).finish(any(),any(),any(),any(),any());
        when(files.download(eq(tenant),any())).thenReturn(java.nio.file.Path.of("missing-test-file"));
        var upload=mock(com.lynx.ad.modules.creative.domain.port.AssetUploadPort.class);when(upload.upload(any(),any(),any(),any(),any(),any(),anyBoolean())).thenReturn(new com.lynx.ad.modules.creative.domain.port.AssetUploadPort.Result("one","11","request"));
        var worker=new AssetSyncWorker(failing,files,credentials,upload);try{worker.process(queued);}finally{worker.stop();}
        assertEquals("UNCERTAIN",sync().status());assertTrue(repository.due(10).isEmpty());repository.recoverExpired();assertEquals("UNCERTAIN",sync().status());assertEquals(0,repository.retry("tenant","asset","account",false));
        when(lookup.find(any(),any())).thenReturn(new AssetReceiptLookupPort.Result("CONFIRMED","one","11"));assertEquals("CONFIRMED",service.reconcile(tenant,"asset","account").state());assertEquals("UPLOADED",sync().status());verify(upload,times(1)).upload(any(),any(),any(),any(),any(),any(),anyBoolean());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void backgroundQueueRetriesApprovedDefiniteFailuresButNeverAmbiguousOrExhaustedAttempts(String mode)throws Exception {
        database(mode);insert("tenant","exhausted","account");insert("tenant","unchecked","account");
        jdbc.update("INSERT INTO creative_asset_review(asset_id,tenant_id,status,next_attempt_at,created_at,updated_at) VALUES('asset','tenant','APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),('exhausted','tenant','APPROVED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        assertTrue(repository.uncertainSyncs().size()<=2);assertTrue(repository.uncertainSyncs().stream().allMatch(s->"UNCERTAIN".equals(s.status())));
        jdbc.update("UPDATE creative_asset_platform SET status='FAILED',next_attempt_at=? WHERE tenant_id='tenant'",Timestamp.from(Instant.now().minusSeconds(1)));
        jdbc.update("UPDATE creative_asset_platform SET attempts=3 WHERE asset_id='exhausted'");
        repository.resumeRecoverableSyncs();assertEquals("QUEUED",sync().status());assertEquals("FAILED",repository.syncs("tenant","exhausted").getFirst().status());assertEquals("FAILED",repository.syncs("tenant","unchecked").getFirst().status());
        jdbc.update("UPDATE creative_asset_platform SET status='UNCERTAIN' WHERE asset_id='asset'");repository.resumeRecoverableSyncs();assertEquals("UNCERTAIN",sync().status());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void repairedUploadUsesItsOwnDurableSignatureAndDimensionsWhenReconciling(String mode)throws Exception {
        database(mode);String repairedHash="a".repeat(32);
        jdbc.update("UPDATE creative_asset_platform SET attempted_signature=? WHERE asset_id='asset'",repairedHash);
        var original=repository.require("tenant","asset");var now=Instant.now();
        var repaired=new AssetUploadRepository.Asset("repair","tenant","user","sync-format-v1:asset","IMAGE","compatible.jpg","image/jpeg",30,"key-repair","STORED","local",null,null,repairedHash,true,720,720,null,null,null,now,now);
        when(files.syncPayloadForSignature(tenant,original,repairedHash)).thenReturn(repaired);
        when(lookup.find(any(),any())).thenReturn(new AssetReceiptLookupPort.Result("CONFIRMED","repaired-file","22"));
        assertEquals("CONFIRMED",service.reconcile(tenant,"asset","account").state());
        verify(lookup).find(any(),argThat(expected->repairedHash.equals(expected.signature())&&expected.sizeBytes()==30&&expected.width()==720));
        assertEquals("repaired-file",sync().objectId());assertEquals(HASH,repository.require("tenant","asset").md5());verify(files,never()).download(any(),any());
    }
}
