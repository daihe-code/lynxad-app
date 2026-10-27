package com.lynx.ad.modules.delivery.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.application.PlanSyncService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;

/** Real SQL, mapper and sync across two independent tenants, without platform writes. */
class PlanOriginStoreTest {
    final TenantId viewer=new TenantId("viewer"),publisher=new TenantId("publisher");
    final AccountId account=new AccountId("viewer-account");
    final ObjectMapper json=new ObjectMapper();
    JdbcTemplate jdbc;PlanOriginStore origins;MpAdPlanRepository plans;DataSourceTransactionManager manager;
    void setup(String mode)throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:plan_origin_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE oauth_account(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),platform VARCHAR(32),advertiser_id VARCHAR(64),grant_id VARCHAR(64),status VARCHAR(32))");
        jdbc.execute("""
            CREATE TABLE ad_plan(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),oauth_account_id VARCHAR(64),aweme_id VARCHAR(64),name VARCHAR(255),
              status VARCHAR(32),qc_status VARCHAR(64),qc_plan_id VARCHAR(64),smart_bid_type VARCHAR(64),budget DECIMAL(18,2),roi2_goal DECIMAL(18,4),
              schedule_type VARCHAR(32),start_date DATE,end_date DATE,publish_version INT,idempotency_key VARCHAR(64),reject_reason VARCHAR(255),source VARCHAR(32),created_at TIMESTAMP,delivery_platform VARCHAR(32))
            """);
        jdbc.execute("CREATE TABLE delivery_publish_attempt(tenant_id VARCHAR(64),plan_id VARCHAR(64),qc_plan_id VARCHAR(64),status VARCHAR(32))");
        jdbc.execute("CREATE TABLE delivery_transfer_attempt(tenant_id VARCHAR(64),target_plan_id VARCHAR(64),qc_plan_id VARCHAR(64),payload_json TEXT)");
        account("publisher-account",publisher,"123","QIANCHUAN","live-publisher");account(account.value(),viewer,"123","QIANCHUAN","live-viewer");
        origins=new PlanOriginStore(jdbc,json);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(AdPlanMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        plans=new MpAdPlanRepository(new SqlSessionTemplate(factory.getObject()).getMapper(AdPlanMapper.class),mock(AdPlanCreativeBindingStore.class));plans.originStore(origins);
    }
    void account(String id,TenantId tenant,String advertiser,String platform,String grant){jdbc.update("INSERT INTO oauth_account VALUES(?,?,?,?,?,?)",id,tenant.value(),platform,advertiser,grant,"ACTIVE");}
    AdPlan plan(String id,TenantId tenant,String account,String qc,PlanSource source,int version){
        var p=new AdPlan(new PlanId(id),tenant,new AccountId(account),new AwemeId("999"),new PlanName("同名商品计划"),PlanStatus.PAUSED,source,
            new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(300),Optional.of(new RoiGoal(new java.math.BigDecimal("4.8"))),Schedule.fromNow(),AudienceJson.empty()),
            List.of(),new QcPlanId(qc),"DISABLE",null,version,null,List.of());plans.save(p);return p;
    }
    void receipt(String id,String qc,String state){jdbc.update("INSERT INTO delivery_publish_attempt VALUES(?,?,?,?)",publisher.value(),id,qc,state);}

    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void publicationSourceIsSharedButPlanDataAndOwnershipRemainTenantScoped(String mode)throws Exception {
        setup(mode);plan("original",publisher,"publisher-account","111",PlanSource.LINGTOU,0);receipt("original","111","CREATED");
        var copy=plan("copy",viewer,account.value(),"111",PlanSource.EXTERNAL,0);
        plan("outside",viewer,account.value(),"222",PlanSource.EXTERNAL,0);
        assertEquals(Set.of("111"),origins.publishedIds(viewer,account,List.of("111","222")));
        assertEquals(PlanSource.LINGTOU,plans.findById(viewer,copy.id()).orElseThrow().source());
        assertEquals(PlanSource.LINGTOU,plans.findByIdForUpdate(viewer,copy.id()).orElseThrow().source());
        assertEquals(PlanSource.LINGTOU,plans.findByQcPlanId(viewer,new QcPlanId("111")).orElseThrow().source());
        assertEquals(PlanSource.LINGTOU,plans.findByIdOnly(copy.id()).orElseThrow().source());
        var listed=plans.findByTenant(viewer);assertEquals(2,listed.size());assertTrue(listed.stream().noneMatch(p->p.id().value().equals("original")));
        assertEquals(PlanSource.EXTERNAL,listed.stream().filter(p->p.id().value().equals("outside")).findFirst().orElseThrow().source());
        assertTrue(plans.findById(viewer,new PlanId("original")).isEmpty());
        assertTrue(origins.publishedIds(viewer,new AccountId("publisher-account"),List.of("111")).isEmpty());
        assertEquals("EXTERNAL",jdbc.queryForObject("SELECT source FROM ad_plan WHERE id='copy'",String.class));
        assertEquals(viewer,plans.findById(viewer,copy.id()).orElseThrow().tenantId());
    }

    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void matchingNamesIdsFailedSubmissionsAndDemoAccountsAreNotPublicationEvidence(String mode)throws Exception {
        setup(mode);
        account("other-advertiser",publisher,"456","QIANCHUAN","live-other");
        account("other-platform",publisher,"123","OCEANENGINE","live-other-platform");
        account("demo",publisher,"123","QIANCHUAN",null);
        for(String id:List.of("other-advertiser","other-platform","demo")) {plan(id,publisher,id,"111",PlanSource.LINGTOU,1);receipt(id,"111","CREATED");}
        plan("failed",publisher,"publisher-account","222",PlanSource.LINGTOU,0);receipt("failed","222","FAILED");
        plan("unpublished",publisher,"publisher-account","333",PlanSource.LINGTOU,0);
        assertTrue(origins.publishedIds(viewer,account,List.of("111","222","333")).isEmpty());
        assertTrue(origins.publishedIds(viewer,account,List.of()).isEmpty());
        plan("legacy-created",publisher,"publisher-account","444",PlanSource.LINGTOU,1);
        jdbc.update("UPDATE oauth_account SET status='UNBOUND' WHERE id='publisher-account'");
        assertEquals(Set.of("444"),origins.publishedIds(viewer,account,List.of("111","444")));
    }

    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void onlyTransfersThatCreatedNewPlansCountAsLingtouPublications(String mode)throws Exception {
        setup(mode);
        for(String qc:List.of("111","222","333","444"))plan("transfer-"+qc,publisher,"publisher-account",qc,PlanSource.EXTERNAL,0);
        jdbc.update("INSERT INTO delivery_transfer_attempt VALUES(?,?,?,?)",publisher.value(),"transfer-111","111","{\"multi_product_creative_list\":[{\"product_id\":123}]}");
        jdbc.update("INSERT INTO delivery_transfer_attempt VALUES(?,?,?,?)",publisher.value(),"transfer-222","222","{}");
        jdbc.update("INSERT INTO delivery_transfer_attempt VALUES(?,?,?,?)",publisher.value(),"transfer-333","333","broken-json");
        jdbc.update("INSERT INTO delivery_transfer_attempt VALUES(?,?,?,?)",publisher.value(),"transfer-444","444",null);
        assertEquals(Set.of("111"),origins.publishedIds(viewer,account,List.of("111","222","333","444")));
    }

    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void firstSyncAndExistingImportedPlansKeepTheVerifiedSource(String mode)throws Exception {
        setup(mode);plan("original",publisher,"publisher-account","111",PlanSource.LINGTOU,0);receipt("original","111","CREATED");
        var accounts=mock(OauthAccountRepository.class);var credentials=mock(QianchuanCredentialFacade.class);var list=mock(AdListPort.class);var management=mock(AdManagementPort.class);var remote=mock(PlanRemoteStore.class);
        var authorized=new OauthAccount(account,viewer,Platform.QIANCHUAN,new AdvertiserId("123"),"测试账户",null,null,Instant.now().plusSeconds(3600),null,"ACTIVE",List.of()).withAuthorizationBinding("live-viewer","app",null);
        var credential=new Credential("test-token",new AdvertiserId("123"));when(accounts.findByTenant(viewer)).thenReturn(List.of(authorized));when(credentials.getUsableCredential(viewer,account)).thenReturn(credential);
        var rows=new ArrayList<OceanPlanRow>();for(String qc:List.of("111","222"))rows.add(new OceanPlanRow(qc,"平台计划","DISABLE",SmartBidType.CUSTOM,Money.ofYuan(300),null,null,json.readTree("{\"ad_info\":{\"budget\":300,\"roi2_goal\":4.8}}")));
        when(list.listIncludingDeleted(credential,null)).thenReturn(rows);
        var sync=new PlanSyncService(plans,accounts,credentials,list,management,remote,manager);sync.originStore(origins);
        assertEquals(2,sync.sync(viewer,null).accounts().getFirst().plans());
        assertEquals("LINGTOU",jdbc.queryForObject("SELECT source FROM ad_plan WHERE tenant_id='viewer' AND qc_plan_id='111'",String.class));
        assertEquals("EXTERNAL",jdbc.queryForObject("SELECT source FROM ad_plan WHERE tenant_id='viewer' AND qc_plan_id='222'",String.class));
        jdbc.update("UPDATE ad_plan SET source='EXTERNAL' WHERE tenant_id='viewer' AND qc_plan_id='111'");
        assertEquals(2,sync.sync(viewer,null).accounts().getFirst().plans());
        assertEquals("LINGTOU",jdbc.queryForObject("SELECT source FROM ad_plan WHERE tenant_id='viewer' AND qc_plan_id='111'",String.class));
        verify(management,never()).settings(any(),anyString(),any(),any(),any());
    }
}
