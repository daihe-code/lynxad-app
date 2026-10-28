package com.lynx.ad.modules.delivery.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.creative.application.LibraryQueryService;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PlanMaterialUsageStoreTest {
    private final ObjectMapper json=new ObjectMapper();
    private JdbcTemplate jdbc;
    private PlanMaterialUsageStore store;
    @BeforeEach void setup() throws Exception {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V47__plan_material_usage.sql")));
        jdbc.execute("CREATE TABLE oauth_account(id varchar,tenant_id varchar,advertiser_id varchar,platform varchar,status varchar,grant_id varchar)");
        jdbc.execute("CREATE TABLE ad_plan(id varchar,tenant_id varchar,oauth_account_id varchar,qc_plan_id varchar,name varchar,status varchar)");
        jdbc.execute("CREATE TABLE creative_carousel_platform(tenant_id varchar,account_id varchar,carousel_id varchar,qc_carousel_id varchar,status varchar)");
        jdbc.execute("CREATE TABLE creative_carousel_part(tenant_id varchar,carousel_id varchar,asset_id varchar,role varchar)");
        jdbc.execute("CREATE TABLE creative_asset_platform(tenant_id varchar,account_id varchar,advertiser_id varchar,asset_id varchar,object_id varchar,material_id varchar,status varchar)");
        account("viewer","a","advertiser");plan("viewer","a","plan","101","PAUSED");
        store=new PlanMaterialUsageStore(jdbc,json);
    }
    void account(String tenant,String id,String advertiser) {jdbc.update("INSERT INTO oauth_account VALUES(?,?,?,'QIANCHUAN','ACTIVE','real-grant')",id,tenant,advertiser);}
    void plan(String tenant,String account,String id,String nativeId,String status) {jdbc.update("INSERT INTO ad_plan VALUES(?,?,?,?,?,?)",id,tenant,account,nativeId,"鎶曟斁璁″垝-"+id,status);}
    PlanMaterialUsageStore.Job snapshot(String plan,String body) throws Exception {
        store.discover();dueNow(plan);
        var job=store.due(20).stream().filter(j->j.planId().equals(plan)).findFirst().orElseThrow();
        assertTrue(store.claim(job));store.success(job,json.readTree(body));return job;
    }
    void dueNow(String plan) {jdbc.update("UPDATE qc_plan_material_usage SET next_sync_at=? WHERE plan_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),plan);}
    static String detail(String id,String video) {return "{\"ad_id\":\""+id+"\",\"multi_product_creative_list\":[{\"video_material\":[{\"video_id\":\""+video+"\",\"aweme_item_id\":0,\"video_cover_id\":\"cover\"}],\"image_material\":[{\"image_ids\":[\"image\"]}],\"carousel_material\":[{\"carousel_id\":7698307529424158771}]}]}";}
    PlanMaterialUsageStore.Usage lookup(String account,String type,String id) {return store.index("viewer",account).lookup(List.of(new PlanMaterialUsageStore.Key(account,type,id)));}

    @Test void importsExactNativeBindingsIncludingFinishedPlansAndCovers() throws Exception {
        snapshot("plan",detail("101","video"));
        for(var key:List.of(new String[]{"VIDEO","video"},new String[]{"IMAGE","cover"},new String[]{"IMAGE","image"},new String[]{"CAROUSEL","7698307529424158771"})) {
            var usage=lookup("a",key[0],key[1]);assertTrue(usage.known());assertEquals("plan",usage.references().getFirst().planId());
        }
        assertTrue(lookup("a","VIDEO","0").references().isEmpty());
        assertTrue(lookup("a","IMAGE","video").references().isEmpty());
        jdbc.update("UPDATE ad_plan SET status='FINISHED' WHERE id='plan'");
        assertEquals("FINISHED",lookup("a","VIDEO","video").references().getFirst().status());
        jdbc.update("UPDATE ad_plan SET status='DELETED' WHERE id='plan'");
        assertTrue(lookup("a","VIDEO","video").references().isEmpty());
    }
    @Test void missingFailedAndIncompleteReadsNeverProveUnused() throws Exception {
        assertFalse(lookup("a","VIDEO","video").known());
        var job=snapshot("plan",detail("101","video"));
        store.failure(job); // Completed lease cannot be overwritten by an old failure.
        assertTrue(lookup("a","VIDEO","video").known());
        store.invalidate("viewer","plan");dueNow("plan");job=store.due(1).getFirst();assertTrue(store.claim(job));store.failure(job);
        assertFalse(lookup("a","VIDEO","other").known());assertEquals(1,lookup("a","VIDEO","video").references().size());
        assertThrows(IllegalArgumentException.class,()->PlanMaterialUsageStore.bindings("101",json.readTree("{\"ad_id\":102,\"multi_product_creative_list\":[]}")));
        assertThrows(IllegalArgumentException.class,()->PlanMaterialUsageStore.bindings("101",json.readTree("{\"ad_id\":101}")));
        assertThrows(IllegalArgumentException.class,()->PlanMaterialUsageStore.bindings("101",json.readTree("{\"ad_id\":101,\"multi_product_creative_list\":[{\"product_id\":1}]}")));
    }
    @Test void nativeRemovalReplacesBindingsAndReadbackWinsOverOlderWorker() throws Exception {
        snapshot("plan",detail("101","old"));
        store.invalidate("viewer","plan");dueNow("plan");var old=store.due(1).getFirst();assertTrue(store.claim(old));
        store.observe("viewer","plan",json.readTree(detail("101","new")));
        store.success(old,json.readTree(detail("101","old")));
        assertEquals(0,lookup("a","VIDEO","old").references().size());assertEquals(1,lookup("a","VIDEO","new").references().size());
        snapshot("plan","{\"ad_id\":101,\"multi_product_creative_list\":[]}");
        assertTrue(lookup("a","VIDEO","new").known());assertTrue(lookup("a","VIDEO","new").references().isEmpty());
    }
    @Test void accountAndTenantBoundariesAndRevokedGrantsAreEnforced() throws Exception {
        snapshot("plan",detail("101","same-id"));
        account("viewer","b","different-advertiser");plan("viewer","b","p2","202","FINISHED");snapshot("p2",detail("202","b-only"));
        account("stranger","c","advertiser");plan("stranger","c","private","303","PAUSED");snapshot("private",detail("303","private-video"));
        assertTrue(lookup("b","VIDEO","same-id").references().isEmpty());
        assertTrue(store.index("viewer",null).lookup(List.of(new PlanMaterialUsageStore.Key("c","VIDEO","private-video"))).references().isEmpty());
        jdbc.update("UPDATE oauth_account SET status='REVOKED' WHERE id='a'");
        assertTrue(lookup("a","VIDEO","same-id").references().isEmpty());
    }
    @Test void nativeCarouselReceiptsConnectSharedComponentsWithoutExposingPublisherPlans() throws Exception {
        snapshot("plan",detail("101","video"));account("publisher","pub","advertiser");
        jdbc.update("INSERT INTO creative_carousel_platform VALUES('publisher','pub','private-group','7698307529424158771','SUCCEEDED')");
        jdbc.update("INSERT INTO creative_carousel_part VALUES('publisher','private-group','private-asset','IMAGE')");
        jdbc.update("INSERT INTO creative_asset_platform VALUES('publisher','pub','advertiser','private-asset','component','material-component','UPLOADED')");
        assertEquals("plan",lookup("a","IMAGE","component").references().getFirst().planId());
        assertTrue(lookup("a","CAROUSEL","private-group").references().isEmpty());
        assertTrue(lookup("a","IMAGE","private-asset").references().isEmpty());
        jdbc.update("UPDATE creative_asset_platform SET advertiser_id='other'");
        assertTrue(lookup("a","IMAGE","component").references().isEmpty());
    }
    @Test void libraryReturnsRealReferencesAndConsistentUsedFilterCounts() throws Exception {
        snapshot("plan",detail("101","video"));
        var oauth=mock(OauthQueryService.class);when(oauth.hasBoundQianchuan(new TenantId("viewer"))).thenReturn(true);
        var catalog=mock(PlatformMaterialCatalog.class);
        when(catalog.list("viewer")).thenReturn(List.of(material("video"),material("unused")));
        var bindings=mock(PlanCreativeBindingQuery.class);
        var service=new LibraryQueryService(mock(CreativeTaskRepository.class),mock(StoragePort.class),mock(MaterialLibraryPort.class),oauth,bindings,mock(ProductQueryService.class));
        service.catalogDependency(catalog);service.usageDependency(store);
        var response=service.query(new TenantId("viewer"),"VIDEO","QIANCHUAN",null,null,"USED",null,1,8,false,"a",null,"ALL");
        assertEquals(1,response.statusCounts().used());assertEquals(1,response.statusCounts().unused());
        assertEquals(1,response.items().size());assertEquals("plan",response.items().getFirst().references().getFirst().planId());
        verify(bindings,never()).usedKeys(any());
        store.invalidate("viewer","plan");
        response=service.query(new TenantId("viewer"),"VIDEO","QIANCHUAN",null,null,"UNUSED",null,1,8,false,"a",null,"ALL");
        assertEquals(0,response.statusCounts().unused());assertTrue(response.items().isEmpty());
    }
    PlatformMaterialCatalog.Material material(String id) {return new PlatformMaterialCatalog.Material("viewer","a","advertiser",MaterialType.VIDEO,id,id,null,id+".mp4",100,720,1280,18,"VIDEO_VERTICAL","NONE",Instant.now());}
    @Test void leasePreventsDuplicateReplicaReads() {
        store.discover();dueNow("plan");var job=store.due(1).getFirst();assertTrue(store.claim(job));assertFalse(store.claim(job));assertTrue(store.due(1).isEmpty());
    }
}
