package com.lynx.ad.modules.delivery.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

class PlanMaterialPresentationStoreTest {
    JdbcTemplate jdbc;PlanMaterialPresentationStore store;
    final TenantId viewer=new TenantId("viewer-tenant");final AccountId account=new AccountId("viewer-account");
    final List<String> copy=List.of("通用商品标题","跑步背包不晃","另一个投放标题");
    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:labels-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(ds);store=new PlanMaterialPresentationStore(jdbc,new ObjectMapper());
        jdbc.execute("CREATE TABLE oauth_account(id varchar,tenant_id varchar,platform varchar,advertiser_id varchar,status varchar,grant_id varchar)");
        jdbc.execute("CREATE TABLE ad_plan(id varchar,tenant_id varchar,oauth_account_id varchar,qc_plan_id varchar,source varchar,publish_version int)");
        jdbc.execute("CREATE TABLE delivery_creation(tenant_id varchar,plan_id varchar,state_json varchar)");
        jdbc.execute("CREATE TABLE creative_asset_platform(asset_id varchar,tenant_id varchar,account_id varchar,advertiser_id varchar,status varchar,object_id varchar)");
        jdbc.execute("CREATE TABLE creative_asset(tenant_id varchar,id varchar,kind varchar,storage_status varchar)");
        jdbc.execute("CREATE TABLE delivery_publish_attempt(tenant_id varchar,plan_id varchar,qc_plan_id varchar,status varchar)");
        jdbc.update("INSERT INTO oauth_account VALUES('publisher','publisher-tenant','QIANCHUAN','advertiser','ACTIVE','grant-p'),('viewer-account','viewer-tenant','QIANCHUAN','advertiser','ACTIVE','grant-v')");
        jdbc.update("INSERT INTO ad_plan VALUES('published-plan','publisher-tenant','publisher','123','LINGTOU',0)");
        jdbc.update("INSERT INTO delivery_publish_attempt VALUES('publisher-tenant','published-plan','123','CREATED')");
        jdbc.update("INSERT INTO creative_asset VALUES('publisher-tenant','video-asset','VIDEO','STORED'),('publisher-tenant','image-asset','IMAGE','STORED')");
        jdbc.update("INSERT INTO creative_asset_platform VALUES('video-asset','publisher-tenant','publisher','advertiser','UPLOADED','native-video'),('image-asset','publisher-tenant','publisher','advertiser','UPLOADED','tos/native-image')");
        jdbc.update("INSERT INTO delivery_creation VALUES('publisher-tenant','published-plan',?)","""
            {"privatePrompt":"not part of the public label","materials":[
             {"type":"VIDEO","productId":"456","title":"跑步背包不晃","titles":["跑步背包不晃","未投放的内部标题"],"videoAssetId":"video-asset"},
             {"type":"IMAGE","productId":"456","title":"轻便双肩包商品图","imageAssetIds":["image-asset"]}]}
            """);
    }
    Optional<PlanMaterialPresentationStore.Label> video(){return store.label(viewer,account,"123","VIDEO","native-video",List.of("456"),copy);}
    @Test void restoresExactPublishedLabelAcrossLoginsWithSameAuthorizedAdvertiser() throws Exception {
        var label=video().orElseThrow();assertEquals("跑步背包不晃",label.name());
        assertEquals(List.of("跑步背包不晃","通用商品标题","另一个投放标题"),label.titles());
        assertFalse(new ObjectMapper().writeValueAsString(label).contains("内部标题"));
        assertFalse(new ObjectMapper().writeValueAsString(label).contains("privatePrompt"));
        var image=store.label(viewer,account,"123","IMAGE","tos/native-image",List.of("456"),List.of("通用商品标题")).orElseThrow();
        assertEquals("轻便双肩包商品图",image.name());assertEquals(List.of("通用商品标题"),image.titles());
    }
    @Test void ownershipAdvertiserAuthorizationAndUploadReceiptMustAllMatch() {
        assertTrue(store.label(new TenantId("other"),account,"123","VIDEO","native-video",List.of("456"),copy).isEmpty());
        jdbc.update("UPDATE oauth_account SET advertiser_id='other' WHERE id='viewer-account'");assertTrue(video().isEmpty());
        jdbc.update("UPDATE oauth_account SET advertiser_id='advertiser',status='REVOKED' WHERE id='viewer-account'");assertTrue(video().isEmpty());
        jdbc.update("UPDATE oauth_account SET status='ACTIVE',grant_id=NULL WHERE id='viewer-account'");assertTrue(video().isEmpty());
        jdbc.update("UPDATE oauth_account SET grant_id='grant-v' WHERE id='viewer-account'");
        jdbc.update("UPDATE creative_asset_platform SET advertiser_id='other'");assertTrue(video().isEmpty());
    }
    @Test void planProductObjectAndConfirmedPublicationCannotBeGuessedFromName() {
        assertTrue(store.label(viewer,account,"999","VIDEO","native-video",List.of("456"),copy).isEmpty());
        assertTrue(store.label(viewer,account,"123","VIDEO","another-video",List.of("456"),copy).isEmpty());
        assertTrue(store.label(viewer,account,"123","VIDEO","native-video",List.of("another-product"),copy).isEmpty());
        jdbc.update("UPDATE delivery_publish_attempt SET status='UNKNOWN'");assertTrue(video().isEmpty());
        jdbc.update("UPDATE delivery_publish_attempt SET status='CREATED',qc_plan_id='999'");assertTrue(video().isEmpty());
    }
    @Test void ambiguousOrInvalidSnapshotsRetainNativeLabel() {
        jdbc.update("UPDATE delivery_creation SET state_json=?","""
            {"materials":[{"type":"VIDEO","productId":"456","title":"一个标题","videoAssetId":"video-asset"},
                          {"type":"VIDEO","productId":"456","title":"另一个标题","videoAssetId":"video-asset"}]}
            """);assertTrue(video().isEmpty());
        jdbc.update("UPDATE delivery_creation SET state_json='invalid'");assertTrue(video().isEmpty());
    }
}
