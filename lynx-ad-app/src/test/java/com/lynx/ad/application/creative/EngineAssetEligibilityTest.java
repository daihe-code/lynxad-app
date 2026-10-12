package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker=true)
class EngineAssetEligibilityTest {
    @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.0");
    static JdbcTemplate jdbc;static AssetUploadRepository repository;
    @BeforeAll static void create() throws Exception {
        var ds=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
        try(var c=ds.getConnection()){for(String script:new String[]{"V12__creative_asset_upload.sql","V14__asset_ai_review.sql","V15__creative_carousel.sql","V19__engine_asset_reference.sql","V18__creation_workflow.sql","V20__asset_storage_metadata.sql","V21__engine_asset_versions.sql"})ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/"+script));}
        jdbc=new JdbcTemplate(ds);repository=new AssetUploadRepository(jdbc);
    }
    @BeforeEach void reset(){jdbc.update("DELETE FROM creative_asset_platform");jdbc.update("DELETE FROM creative_asset_review");jdbc.update("DELETE FROM creative_engine_asset");jdbc.update("DELETE FROM creative_asset");}
    Asset asset(String id,String mode){var now=Instant.now();return new Asset(id,"tenant","user",id+"-request","IMAGE","image.png","image/png",4,id,"STORED",mode,null,null,null,true,1080,1080,null,null,null,now,now);}
    @Test void differentFileVersionsKeepSeparateAssetsAndPlatformReceipts() {
        var old=asset("old","engine_oss");var latest=asset("latest","engine_oss");
        repository.registerEngine(old,new EngineReference(old.id(),"tenant","job","mat","IMAGE_0",null));
        repository.addSync(old,"account");
        jdbc.update("UPDATE creative_asset_platform SET status='UPLOADED',object_id='old-platform-image' WHERE asset_id='old'");
        repository.registerEngine(latest,new EngineReference(latest.id(),"tenant","job","mat","IMAGE_0",null));
        repository.addSync(latest,"account");repository.activate("tenant",latest.id());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM creative_engine_asset",Integer.class));
        assertEquals("old-platform-image",repository.syncs("tenant","old").getFirst().objectId());
        assertEquals("QUEUED",repository.syncs("tenant","latest").getFirst().status());
        assertNull(repository.syncs("tenant","latest").getFirst().objectId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM creative_asset_review",Integer.class));
    }
    @Test void localTextUploadStoresContentInDatabaseAndNeverWritesOss() {
        var storage=org.mockito.Mockito.mock(com.lynx.ad.modules.creative.domain.port.StoragePort.class);
        var accounts=org.mockito.Mockito.mock(com.lynx.ad.modules.identity.domain.port.OauthAccountRepository.class);
        var service=new com.lynx.ad.modules.creative.application.AssetUploadService(repository,storage,accounts,new com.lynx.ad.common.config.LynxAdProperties(),new com.fasterxml.jackson.databind.ObjectMapper(),org.mockito.Mockito.mock(com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector.class));
        var tenant=new com.lynx.ad.common.domain.Ids.TenantId("tenant");
        com.lynx.ad.common.security.TenantContext.set(new com.lynx.ad.common.security.TenantPrincipal(new com.lynx.ad.common.domain.Ids.UserId("user"),java.util.Optional.of(tenant),java.util.Optional.of(com.lynx.ad.common.security.MemberRole.OWNER),com.lynx.ad.common.security.SysRole.USER));
        try {
            byte[] text="真实商品介绍".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var init=service.init(tenant,new com.lynx.ad.modules.creative.application.AssetUploadService.InitRequest("text-upload-request-01","copy.txt",text.length,false,java.util.List.of(),"TEXT"));
            var detail=service.upload(tenant,init.assetId(),new org.springframework.mock.web.MockMultipartFile("file","copy.txt","text/plain",text));
            assertEquals("真实商品介绍",detail.textContent());assertEquals("DB",detail.storageMetadata().pathVersion());
            assertNull(detail.storageMetadata().bucket());assertNull(detail.storageMetadata().objectKey());
            assertEquals("QUEUED",detail.aiReview().status());assertTrue(detail.syncs().isEmpty());
            org.mockito.Mockito.verifyNoInteractions(storage);
        }finally{com.lynx.ad.common.security.TenantContext.clear();}
    }
    @Test void registeredEngineAssetSyncsWithoutCreatingOrRecoveringAnUploadReview() {
        var a=asset("engine","engine_oss");repository.registerEngine(a,new EngineReference(a.id(),"tenant","job","mat","IMAGE_0","https://example.test/image.png"));
        repository.addSync(a,"account");repository.activate("tenant",a.id());
        repository.queueReview("tenant",a.id());repository.recoverReviews();repository.recoverExpired();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM creative_asset_review",Integer.class));
        assertEquals("NOT_REQUIRED",repository.aiReview("tenant",a.id()).status());assertTrue(repository.approved("tenant",a.id()));
        var sync=repository.due(10).getFirst();assertTrue(repository.claim(sync));
        repository.engineSignature("tenant",a.id(),"01234567890123456789012345678901");assertNotNull(repository.require("tenant",a.id()).md5());
        assertThrows(BizException.class,()->repository.engineReference("other",a.id()));
    }
    @Test void uploadedAigcFlagDoesNotBypassReviewEvenIfStorageModeIsForged() {
        for(String mode:new String[]{"oss","engine_oss"}) {
            var a=asset("upload-"+mode,mode);repository.insert(a);repository.addSync(a,"account");repository.activate("tenant",a.id());
            assertFalse(repository.approved("tenant",a.id()));repository.queueReview("tenant",a.id());
            assertEquals("QUEUED",repository.aiReview("tenant",a.id()).status());
        }
        assertTrue(repository.due(10).isEmpty());assertEquals(2,repository.dueReviews(10).size());
        assertTrue(repository.claimReview(repository.dueReviews(10).getFirst()));
    }
    @Test void storageLocationsAndPerAdvertiserReceiptsSurviveDatabaseRoundTrip() {
        var a=asset("real-location","engine_oss");
        repository.registerEngine(a,new EngineReference(a.id(),"tenant","job","mat","IMAGE_0",null),
                new StorageMetadata("lynxad-douyin-image","https://oss-cn-beijing.aliyuncs.com","V2","AI_GENERATED",1200L,"etag",null));
        assertEquals("lynxad-douyin-image",repository.storageMetadata("tenant",a.id()).bucket());
        assertEquals(1200L,repository.storageMetadata("tenant",a.id()).durationMs());
        for(String account:new String[]{"first","second"})repository.addSync(a,account);
        repository.activate("tenant",a.id());
        for(var sync:repository.due(10)) {
            assertTrue(repository.claim(sync));
            repository.finish(sync,sync.accountId()+"-advertiser",sync.accountId()+"-object","9223372036854775806","request");
        }
        repository.addSync(a,"first");
        var receipts=repository.syncs("tenant",a.id());
        assertEquals(2,receipts.size());assertTrue(receipts.stream().allMatch(s->"UPLOADED".equals(s.status())));
        assertEquals(2,receipts.stream().map(Sync::objectId).distinct().count());
        assertEquals("9223372036854775806",receipts.getFirst().materialId());
    }
}
