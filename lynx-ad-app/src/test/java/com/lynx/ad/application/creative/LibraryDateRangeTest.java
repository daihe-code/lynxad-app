package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.creative.application.LibraryQueryService;
import com.lynx.ad.modules.creative.api.dto.LibraryResponse;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LibraryDateRangeTest {
    final TenantId tenant=new TenantId("tenant");
    LibraryQueryService service;
    @BeforeEach void setup() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE oauth_account(id VARCHAR(64),tenant_id VARCHAR(64),platform VARCHAR(32),status VARCHAR(32))");
        jdbc.execute("CREATE TABLE platform_material_catalog(tenant_id VARCHAR(64),account_id VARCHAR(64),advertiser_id VARCHAR(64),material_type VARCHAR(32),entry_key VARCHAR(64),object_id VARCHAR(64),material_id VARCHAR(64),filename VARCHAR(64),size_bytes BIGINT,width INT,height INT,duration_sec INT,image_mode VARCHAR(64),review_badge VARCHAR(64),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE platform_material_sync(account_id VARCHAR(64),tenant_id VARCHAR(64),last_success_at TIMESTAMP,last_attempt_ok BOOLEAN)");
        var catalog=new PlatformMaterialCatalog(jdbc);
        for(String account:List.of("shared-a","shared-b","foreign")) {
            String owner=account.equals("foreign")?"other-tenant":"tenant";
            jdbc.update("INSERT INTO oauth_account VALUES(?,?,?,?)",account,owner,"QIANCHUAN","ACTIVE");
            jdbc.update("INSERT INTO platform_material_sync VALUES(?,?,CURRENT_TIMESTAMP,TRUE)",account,owner);
            for(var type:List.of(MaterialType.VIDEO,MaterialType.IMAGE)) {
                int total=type==MaterialType.VIDEO?30:273,recent=type==MaterialType.VIDEO?2:14;
                var materials=new ArrayList<MaterialLibraryPort.PlatformMaterial>();
                for(int i=0;i<total;i++)materials.add(new MaterialLibraryPort.PlatformMaterial("object-"+i,"material-"+i,"素材-"+i,1024,720,1280,18,"VIDEO_VERTICAL","EXTERNAL",Instant.parse(i<recent?"2026-10-21T04:47:58Z":"2026-07-27T12:27:27Z"),"","",false,"NONE"));
                catalog.replace(owner,account,account,type,materials);
            }
        }
        var oauth=mock(OauthQueryService.class);when(oauth.hasBoundQianchuan(tenant)).thenReturn(true);
        var bindings=mock(PlanCreativeBindingQuery.class);when(bindings.usedKeys(tenant)).thenReturn(Set.of());
        service=new LibraryQueryService(mock(CreativeTaskRepository.class),mock(StoragePort.class),mock(MaterialLibraryPort.class),oauth,bindings,mock(ProductQueryService.class));service.catalogDependency(catalog);
    }
    LibraryResponse query(String account,LocalDate start,LocalDate end,int page) {
        return service.query(tenant,"VIDEO","QIANCHUAN",start,end,"ALL",null,page,8,false,account,null,"ALL");
    }
    @Test void defaultShowsTheCompleteHistoricalCatalogAndPaginationWithoutMixingAccounts() {
        for(String account:List.of("shared-a","shared-b")) {
            var result=query(account,null,null,1);assertEquals(30,result.tabCounts().video());assertEquals(273,result.tabCounts().image());assertEquals(30,result.statusCounts().all());assertEquals(30,result.pageInfo().total());
            Set<String> ids=new HashSet<>();for(int page=1;page<=4;page++)for(var item:query(account,null,null,page).items()) {assertTrue(item.id().startsWith("EXT:"+account+":"));assertTrue(ids.add(item.id()));}
            assertEquals(30,ids.size());assertEquals(6,query(account,null,null,4).items().size());
        }
        assertEquals(0,query("foreign",null,null,1).pageInfo().total());
    }
    @Test void explicitDatesFilterByOriginalUploadTimeAndClearingDatesRestoresAllCounts() {
        var start=LocalDate.of(2026,10,21);var end=LocalDate.of(2026,10,27);
        var recent=query("shared-a",start,end,1);assertEquals(2,recent.pageInfo().total());assertEquals(2,recent.tabCounts().video());assertEquals(14,recent.tabCounts().image());
        var restored=query("shared-a",null,null,1);assertEquals(30,restored.pageInfo().total());assertEquals(273,restored.tabCounts().image());
        assertEquals(30,query("shared-a",null,end,1).pageInfo().total());assertEquals(2,query("shared-a",start,null,1).pageInfo().total());
        assertThrows(BizException.class,()->query("shared-a",end,start,1));
    }
}
