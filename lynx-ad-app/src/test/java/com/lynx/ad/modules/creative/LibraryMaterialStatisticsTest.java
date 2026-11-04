package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.delivery.domain.model.PlanStatisticsRange;
import com.lynx.ad.modules.delivery.domain.port.AdManagementPort;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LibraryMaterialStatisticsTest {
    final TenantId tenant=new TenantId("owner");
    final AssetUploadRepository assets=mock(AssetUploadRepository.class);
    final CarouselRepository carousels=mock(CarouselRepository.class);
    final CarouselPlatformService carouselPlatform=mock(CarouselPlatformService.class);
    final CreativeTaskRepository packages=mock(CreativeTaskRepository.class);
    final PlatformMaterialCatalog catalog=mock(PlatformMaterialCatalog.class);
    final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    final MaterialLibraryPort library=mock(MaterialLibraryPort.class);
    final AdManagementPort reports=mock(AdManagementPort.class);
    final LibraryMaterialStatisticsService service=new LibraryMaterialStatisticsService(assets,carousels,carouselPlatform,catalog,packages,credentials,library,reports);
    final Credential credential=new Credential("private-token",new AdvertiserId("1869861871552519"),"account");
    final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    final PlanStatisticsRange range=new PlanStatisticsRange(today.minusDays(6),today);
    LibraryMaterialStatisticsTest(){when(credentials.getUsableCredential(tenant,new AccountId("account"))).thenReturn(credential);when(reports.libraryMaterialReport(any(),anyString(),anyString(),any(),any())).thenReturn(new AdManagementPort.LibraryMaterialReport(Map.of("cost",java.math.BigDecimal.TEN),List.of(),true,true));}
    AssetUploadRepository.Asset asset(String id,String canonical) {
        var value=new AssetUploadRepository.Asset(id,tenant.value(),"user",id,"VIDEO","file","video/mp4",100,"file","STORED","engine_oss",null,null,"md5",true,720,1280,null,canonical,null,Instant.now(),Instant.now());
        when(assets.require(tenant.value(),id)).thenReturn(value);return value;
    }
    AssetUploadRepository.Sync sync(String account,String object,String material,String advertiser) {
        return new AssetUploadRepository.Sync("receipt",tenant.value(),"asset",account,advertiser,"UPLOADED",object,material,"req","PASS",1,null,null,null);
    }
    String remote(String advertiser) {
        var material=new PlatformMaterialCatalog.Material(tenant.value(),"account",advertiser,MaterialType.VIDEO,"m:123","video-file","123","name",100,720,1280,18,"VERTICAL","NONE",Instant.now());
        when(catalog.find(tenant.value(),"account",MaterialType.VIDEO,"m:123")).thenReturn(Optional.of(material));return "EXT:account:VIDEO:m:123";
    }
    @Test void externalMaterialUsesCatalogIdentityAndCurrentAdvertiser(){var id=remote(credential.advertiserId().value());var result=service.query(tenant,id,"account",range);assertEquals("READY",result.state());assertEquals("123",result.materialId());verify(reports).libraryMaterialReport(credential,"VIDEO","123",range.startDate(),range.endDate());}
    @Test void refusesForgedOrDifferentAccountIds(){var id=remote(credential.advertiserId().value());assertThrows(BizException.class,()->service.query(tenant,id,"other",range));assertThrows(BizException.class,()->service.query(tenant,"EXT:account:VIDEO:m:999","account",range));verifyNoInteractions(credentials,reports);}
    @Test void changedAdvertiserCannotReadOldMaterial(){var id=remote("old-advertiser");assertThrows(BizException.class,()->service.query(tenant,id,"account",range));verifyNoInteractions(reports);}
    @Test void canonicalAssetUsesItsSuccessfulReceiptRatherThanLocalUuid(){asset("alias","canonical");asset("canonical",null);when(assets.syncs(tenant.value(),"canonical")).thenReturn(List.of(sync("account","native-video","123",credential.advertiserId().value())));var result=service.query(tenant,"ENGINE:alias","account",range);assertEquals("123",result.materialId());verifyNoInteractions(library);}
    @Test void missingMaterialIdResolvesOnlyExactFileInSelectedAccount(){asset("asset",null);when(assets.syncs(tenant.value(),"asset")).thenReturn(List.of(sync("account","native-video",null,credential.advertiserId().value())));
        when(library.list(any())).thenReturn(new MaterialLibraryPort.MaterialPage(List.of(new MaterialLibraryPort.PlatformMaterial("wrong-file","888","same-name",100,720,1280,18,null,null,Instant.now(),null,null,true,"NONE"))));
        var result=service.query(tenant,"ENGINE:asset","account",range);assertEquals("NO_DATA",result.state());verifyNoInteractions(reports);
        var query=org.mockito.ArgumentCaptor.forClass(MaterialLibraryPort.MaterialLibraryQuery.class);verify(library).list(query.capture());assertEquals("native-video",query.getValue().objectId());assertEquals(credential.advertiserId().value(),query.getValue().advertiserId());
    }
    @Test void unsyncedMaterialDoesNotQueryWholeAccount(){asset("asset",null);when(assets.syncs(tenant.value(),"asset")).thenReturn(List.of());assertEquals("NO_DATA",service.query(tenant,"ENGINE:asset","account",range).state());verifyNoInteractions(reports,library);}
    @Test void multipleAccountsRequireExplicitSelection(){asset("asset",null);when(assets.syncs(tenant.value(),"asset")).thenReturn(List.of(sync("account","video-a","123",credential.advertiserId().value()),sync("other","video-b","456","other-adv")));assertEquals("ACCOUNT_REQUIRED",service.query(tenant,"ENGINE:asset",null,range).state());verifyNoInteractions(credentials,reports,library);}
    @Test void singleAccountCanBeInferredFromTrustedReceipt(){asset("asset",null);when(assets.syncs(tenant.value(),"asset")).thenReturn(List.of(sync("account","video-a","123",credential.advertiserId().value())));assertEquals("123",service.query(tenant,"ENGINE:asset",null,range).materialId());}
    @Test void carouselUsesSuccessfulGroupReceiptNotImageComponentIds(){when(carousels.require(tenant.value(),"group")).thenReturn(new CarouselRepository.Bundle("group",tenant.value(),"user","request","name",List.of("account"),"APPROVED",Instant.now()));when(carouselPlatform.receipt(tenant,"group","account")).thenReturn(new CarouselPlatformService.Receipt("SUCCEEDED","987"));assertEquals("987",service.query(tenant,"CAROUSEL:group","account",range).materialId());verify(reports).libraryMaterialReport(credential,"CAROUSEL","987",range.startDate(),range.endDate());verifyNoInteractions(library);}
    @Test void invalidDateWindowIsRejected(){assertThrows(BizException.class,()->new PlanStatisticsRange(today,today.minusDays(1)));assertThrows(BizException.class,()->new PlanStatisticsRange(today,today.plusDays(1)));}
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void nativeFileLookupIsTenantAccountAndTypeScoped(String mode) throws Exception {
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:material-stats-"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE oauth_account(id VARCHAR(36),tenant_id VARCHAR(36),platform VARCHAR(16),status VARCHAR(16))");
        try(var connection=ds.getConnection()){org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("db/migration/V33__platform_material_catalog.sql"));}
        var actual=new PlatformMaterialCatalog(jdbc);
        for(String owner:List.of("owner","foreign")) {
            String account=owner.equals("owner")?"account":"foreign-account";
            jdbc.update("INSERT INTO oauth_account VALUES(?,?,?,'ACTIVE')",account,owner,"QIANCHUAN");
            actual.replace(owner,account,"adv",MaterialType.VIDEO,List.of(new MaterialLibraryPort.PlatformMaterial("same-file","123","name",100,720,1280,18,null,"UPLOAD",Instant.now(),null,null,false,"NONE")));
        }
        assertEquals("123",actual.findObject("owner","account",MaterialType.VIDEO,"same-file").orElseThrow().materialId());
        assertTrue(actual.findObject("owner","foreign-account",MaterialType.VIDEO,"same-file").isEmpty());
        assertTrue(actual.findObject("owner","account",MaterialType.IMAGE,"same-file").isEmpty());
        jdbc.update("UPDATE oauth_account SET status='INACTIVE' WHERE id='account'");
        assertTrue(actual.findObject("owner","account",MaterialType.VIDEO,"same-file").isEmpty());
    }
}
