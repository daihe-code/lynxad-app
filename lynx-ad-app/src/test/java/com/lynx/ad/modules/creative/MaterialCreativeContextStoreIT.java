package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.MaterialCreativeContextStore;
import com.lynx.ad.modules.creative.infrastructure.persistence.MaterialCreativeContextStore.Entry;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Uses only a freshly named verification database, never the application's data. */
@EnabledIfEnvironmentVariable(named="LYNXAD_CONTEXT_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/lynxad_context_verify_[a-zA-Z0-9_]+\\?.*")
class MaterialCreativeContextStoreIT {
    static String database,server,user,password;static boolean created;static JdbcTemplate jdbc;static MaterialCreativeContextStore store;
    static final ObjectMapper json=new ObjectMapper();
    @BeforeAll static void setup()throws Exception{
        String url=System.getenv("LYNXAD_CONTEXT_TEST_URL");database=url.substring(url.indexOf("/lynxad_")+1,url.indexOf('?'));if(!database.matches("lynxad_context_verify_[a-zA-Z0-9_]+"))throw new IllegalArgumentException("Unsafe verification schema");
        server=url.replace("/"+database+"?","/?");user=System.getenv("LYNXAD_CONTEXT_TEST_USER");password=System.getenv("LYNXAD_CONTEXT_TEST_PASSWORD");
        try(var c=DriverManager.getConnection(server,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+database);created=true;}
        var ds=new DriverManagerDataSource(url,user,password);jdbc=new JdbcTemplate(ds);try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V31__material_creative_context.sql"));}store=new MaterialCreativeContextStore(jdbc,json);
    }
    @AfterAll static void cleanup()throws Exception{if(created&&database.matches("lynxad_context_verify_[a-zA-Z0-9_]+"))try(var c=DriverManager.getConnection(server,user,password);var s=c.createStatement()){s.execute("DROP DATABASE "+database);}}
    @BeforeEach void clear(){jdbc.update("DELETE FROM material_creative_context");}
    Entry entry(String tenant,String product,String id){return new Entry(id,tenant,"plan","account",product,"material","immutable-asset","PENDING",json.createObjectNode().put("origin","LIBRARY"),null,false,null,1,0);}
    JsonNode profile(){return json.createObjectNode().put("source","VISUAL_ANALYSIS").set("scenes",json.valueToTree(List.of("骑行通勤")));}
    @Test void receiptsReuseOnlySameTenantProductAndAssetVersion(){var e=entry("tenant","p1","one");store.insert(e);store.insert(e);assertEquals(1,store.pending().size());store.finish(e,"READY",profile());assertTrue(store.reusable("tenant","p1","immutable-asset").isPresent());assertTrue(store.reusable("other","p1","immutable-asset").isEmpty());assertTrue(store.reusable("tenant","p2","immutable-asset").isEmpty());assertTrue(store.reusable("tenant","p1","new-asset").isEmpty());}
    @Test void deletedMaterialKeepsSceneAndPerformanceAcrossRestart(){var e=entry("tenant","p1","one");store.insert(e);store.finish(e,"READY",profile());var assessment=json.createObjectNode().put("status","LOSS").put("observed_at","2026-10-13T12:00:00+08:00");store.assessment("tenant","account","one","p1",assessment);store.removed("tenant","plan","material","MANUAL");var restarted=new MaterialCreativeContextStore(jdbc,json);var saved=restarted.get("tenant","one").orElseThrow();assertTrue(saved.removed());assertEquals("MANUAL",saved.removalReason());assertEquals(profile(),saved.profile());assertEquals("LOSS",saved.body().path("assessment").path("status").asText());assertEquals(1,restarted.history("tenant","account").size());}
    @Test void staleCrossAccountOrCrossProductAssessmentCannotOverwriteHistory(){var e=entry("tenant","p1","one");store.insert(e);var newer=json.createObjectNode().put("status","QUALIFIED").put("observed_at","2026-10-13T12:00:00+08:00");var older=json.createObjectNode().put("status","LOSS").put("observed_at","2026-10-12T12:00:00+08:00");store.assessment("other","account","one","p1",newer);store.assessment("tenant","other-account","one","p1",newer);store.assessment("tenant","account","one","p2",newer);assertFalse(store.get("tenant","one").orElseThrow().body().has("assessment"));store.assessment("tenant","account","one","p1",newer);store.assessment("tenant","account","one","p1",older);assertEquals("QUALIFIED",store.get("tenant","one").orElseThrow().body().path("assessment").path("status").asText());}
    @Test void leasedRequestIsUniqueAndOldWorkerCannotOverwriteExplicitRetry(){var e=entry("tenant","p1","one");store.insert(e);assertTrue(store.claim(e));assertFalse(store.claim(e));store.finish(e,"FAILED",null);store.retry("tenant","one");var retry=store.get("tenant","one").orElseThrow();assertEquals(2,retry.version());store.finish(e,"READY",profile());assertEquals("PENDING",store.get("tenant","one").orElseThrow().status());assertTrue(store.claim(retry));store.defer(retry);assertEquals("PENDING",store.get("tenant","one").orElseThrow().status());}
}
