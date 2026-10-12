package com.lynx.ad.modules.delivery.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.domain.port.AgentActionRepository;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdStatusPort;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses only an explicitly named isolated database and a fake platform port. */
@EnabledIfEnvironmentVariable(named="LYNXAD_DELETE_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/lynxad_plan_delete_verify_[a-zA-Z0-9_]+.*")
class PlanDeletionRepositoryIT {
    static JdbcTemplate jdbc;static MpAdPlanRepository plans;static PlanTransferStore transfers;static TransactionTemplate tx;
    static AdPlanCreativeBindingStore bindings;
    final TenantId tenant=new TenantId("tenant-delete");final AccountId account=new AccountId("account");
    final AdStatusPort platform=mock(AdStatusPort.class);final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);
    PlanCommandService command;
    @BeforeAll static void setup() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("LYNXAD_DELETE_TEST_URL"),"root",System.getenv("LYNXAD_TRANSFER_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("""
          CREATE TABLE IF NOT EXISTS ad_plan(
            id VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(64),oauth_account_id VARCHAR(64),aweme_id VARCHAR(64),name VARCHAR(255),
            status VARCHAR(32),qc_status VARCHAR(64),qc_plan_id VARCHAR(64),smart_bid_type VARCHAR(64),budget DECIMAL(18,2),roi2_goal DECIMAL(18,4),
            start_date DATE,end_date DATE,publish_version INT,idempotency_key VARCHAR(64),reject_reason VARCHAR(255),source VARCHAR(32),
            created_at TIMESTAMP NULL,delivery_platform VARCHAR(32))
          """);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ad_plan' AND column_name='name_active'",Integer.class)==0) {
            jdbc.execute("ALTER TABLE ad_plan ADD COLUMN name_active VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN status COLLATE utf8mb4_bin <> 'finished' THEN name ELSE NULL END) STORED");
            jdbc.execute("CREATE UNIQUE INDEX uk_ad_plan_aweme_name ON ad_plan(tenant_id,aweme_id,name_active)");
        }
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V24__deleted_plan_names.sql"));}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='delivery_transfer_group'",Integer.class)==0)
            try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V22__plan_transfer.sql"));}
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(AdPlanMapper.class);factory.setConfiguration(config);
        var session=new SqlSessionTemplate(factory.getObject());bindings=mock(AdPlanCreativeBindingStore.class);
        plans=new MpAdPlanRepository(session.getMapper(AdPlanMapper.class),bindings);
        var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);transfers=new PlanTransferStore(jdbc,manager);
    }
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM delivery_transfer_attempt");jdbc.update("DELETE FROM delivery_transfer_group");jdbc.update("DELETE FROM ad_plan");clearInvocations(bindings);
        command=new PlanCommandService(plans,null,null,null,null,null,mock(BillingTrialService.class),platform,credentials,
                mock(AgentActionRepository.class),new ObjectMapper(),mock(AuditRecorder.class),bindings);command.transferStore(transfers);
        when(credentials.getUsableCredential(tenant,account)).thenReturn(new Credential("fixture-token",new AdvertiserId("999")));
    }
    AdPlan plan(String id,String qc,SmartBidType mode) {
        return new AdPlan(new PlanId(id),tenant,account,new AwemeId("99"),new PlanName("计划"+id),PlanStatus.PAUSED,PlanSource.LINGTOU,
                new DeliverySetting(mode,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(),new QcPlanId(qc),"DISABLE",null,1,null,List.of());
    }
    @Test void acknowledgedDeletePersistsTombstoneWithoutRemovingSharedRootOrCounterpart() {
        var root=plan("root","111",SmartBidType.CONSERVATIVE);var cost=plan("cost","222",SmartBidType.CUSTOM);
        plans.save(root);plans.save(cost);transfers.linkImported(root,cost);
        tx.executeWithoutResult(s->command.deletePlan(tenant,root.id()));
        assertEquals(PlanStatus.DELETED,plans.findById(tenant,root.id()).orElseThrow().status());
        assertEquals(PlanStatus.PAUSED,plans.findById(tenant,cost.id()).orElseThrow().status());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ad_plan",Integer.class));
        var g=transfers.group(tenant,cost.id());assertEquals("root",g.root());assertNull(g.conservative());assertEquals("cost",g.custom());
        verify(platform).updateStatus(any(),eq(new QcPlanId("111")),eq(PlanAction.DELETE));verify(bindings,never()).upsert(any(),any());
    }
    @Test void savingStaleObjectAfterDeleteCannotResurrectPhysicalPlan() {
        var root=plan("root","111",SmartBidType.CONSERVATIVE);plans.save(root);
        var stale=plans.findById(tenant,root.id()).orElseThrow();
        tx.executeWithoutResult(s->command.deletePlan(tenant,root.id()));
        stale.apply(PlanStatus.DELIVERING);plans.save(stale);
        assertEquals(PlanStatus.DELETED,plans.findById(tenant,root.id()).orElseThrow().status());
        assertEquals("DELETE",plans.findById(tenant,root.id()).orElseThrow().qcStatus());
        tx.executeWithoutResult(s->command.deletePlan(tenant,root.id()));
        verify(platform,times(1)).updateStatus(any(),any(),eq(PlanAction.DELETE));
    }
    @Test void platformRejectionRollsBackDeleteAndKeepsSelectedPlan() {
        var root=plan("root","111",SmartBidType.CONSERVATIVE);plans.save(root);
        doThrow(new BizException(1406,"平台拒绝删除")).when(platform).updateStatus(any(),any(),eq(PlanAction.DELETE));
        assertThrows(BizException.class,()->tx.executeWithoutResult(s->command.deletePlan(tenant,root.id())));
        assertEquals(PlanStatus.PAUSED,plans.findById(tenant,root.id()).orElseThrow().status());
        assertNull(transfers.group(tenant,root.id()));
    }
    @Test void deletedPlanNameCanBeReusedWhileActivePlanNamesRemainUnique() {
        var root=plan("root","111",SmartBidType.CONSERVATIVE);plans.save(root);
        var replacement=plan("replacement","222",SmartBidType.CONSERVATIVE);replacement.rename(root.name());
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->plans.save(replacement));
        tx.executeWithoutResult(s->command.deletePlan(tenant,root.id()));
        plans.save(replacement);
        assertTrue(plans.findById(tenant,replacement.id()).isPresent());assertEquals(PlanStatus.DELETED,plans.findById(tenant,root.id()).orElseThrow().status());
    }
}
