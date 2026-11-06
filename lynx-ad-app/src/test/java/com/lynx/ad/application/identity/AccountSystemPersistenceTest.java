package com.lynx.ad.application.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.identity.application.AccountBindingRegistry;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.billing.application.*;
import com.lynx.ad.modules.tenant.application.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

public class AccountSystemPersistenceTest {
    public static String md5(String value) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));}
    record Fixture(JdbcTemplate jdbc,TransactionTemplate tx,AccountBindingRegistry bindings,PointWalletService points) {}
    Fixture fixture(String mode) throws Exception { return fixture(mode,false); }
    Fixture fixture(String mode,boolean duplicates) throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:accounts_"+UUID.randomUUID()+";MODE="+mode+";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE ALIAS HASH_MD5 FOR 'com.lynx.ad.application.identity.AccountSystemPersistenceTest.md5'");
        jdbc.execute("CREATE TABLE sys_tenant(id VARCHAR(36) PRIMARY KEY,name VARCHAR(128),status VARCHAR(16),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(36) PRIMARY KEY,status VARCHAR(16))");
        jdbc.execute("CREATE TABLE tenant_member(tenant_id VARCHAR(36),user_id VARCHAR(36),member_role VARCHAR(16),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id,user_id))");
        jdbc.execute("CREATE TABLE oauth_account(id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(36),platform VARCHAR(24),advertiser_id VARCHAR(64),authorized_by_user_id VARCHAR(36),grant_id VARCHAR(36),status VARCHAR(24))");
        for(String t:List.of("a","b")) {
            jdbc.update("INSERT INTO sys_tenant(id,name,status) VALUES(?,?,'ACTIVE')",t,t);
            jdbc.update("INSERT INTO sys_user VALUES(?,'ACTIVE')",t);
            jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role) VALUES(?,?,'OWNER')",t,t);
        }
        if(duplicates) {
            jdbc.update("INSERT INTO oauth_account VALUES('legacy-a','a','QIANCHUAN','1001','a','direct','ACTIVE')");
            jdbc.update("INSERT INTO oauth_account VALUES('legacy-b','b','QIANCHUAN','1001','b','agency','UNUSABLE')");
            jdbc.update("INSERT INTO oauth_account VALUES('unknown-owner','a','QIANCHUAN','1002','missing',NULL,'ACTIVE')");
        }
        String migration=new String(new ClassPathResource("db/"+(mode.equals("MySQL")?"migration-mysql":"migration")+"/V52__workspace_account_system.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8).replace("md5(","HASH_MD5(");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8)));}
        var foundation=new WorkspaceFoundation(jdbc);var quotas=mock(EntitlementQueryService.class);when(quotas.adAccountLimit(any())).thenReturn(2);
        return new Fixture(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),new AccountBindingRegistry(jdbc,quotas,foundation),new PointWalletService(jdbc,foundation));
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void migrationPreservesIdsAndQuarantinesDuplicateOrUnknownResponsibility(String mode) throws Exception {
        var f=fixture(mode,true);
        assertEquals(3,f.jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_binding WHERE status='REVIEW_REQUIRED'",Integer.class));
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_claim",Integer.class));
        assertEquals("legacy-a",f.bindings.find("a","legacy-a").orElseThrow().id());
        assertThrows(BizException.class,()->f.bindings.executionScope("a","legacy-a","a"));
    }
        OauthAccount account(String tenant,String id,String adv,String status,String user,String grant) {
        return new OauthAccount(new AccountId(id),new TenantId(tenant),Platform.QIANCHUAN,new AdvertiserId(adv),"Account",new EncryptedToken("encrypted"),new EncryptedToken("encrypted-refresh"),Instant.now().plusSeconds(1000),Instant.now().plusSeconds(10000),status,List.of(),user).withAuthorizationBinding(grant,"app",null);
    }
    void bind(Fixture f,String tenant,String id,String adv,String status,String user,String grant) {f.tx.executeWithoutResult(s->f.bindings.synchronize(account(tenant,id,adv,status,user,grant)));}
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void reauthorizationUsesOneSlotAndKeepsResponsibilityAcrossPaths(String mode) throws Exception {
        var f=fixture(mode);bind(f,"a","x","1001","ACTIVE","a","direct");bind(f,"a","x","1001","ACTIVE","a","agency");
        assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_claim",Integer.class));
        assertEquals(2,f.jdbc.queryForObject("SELECT COUNT(*) FROM account_grant_path",Integer.class));
        f.jdbc.update("INSERT INTO sys_user VALUES('colleague','ACTIVE')");f.jdbc.update("INSERT INTO tenant_member(tenant_id,user_id,member_role,status,membership_id) VALUES('a','colleague','OWNER','ACTIVE','m2')");
        assertThrows(BizException.class,()->bind(f,"a","x","1001","ACTIVE","colleague","new"));
        assertEquals("a",f.bindings.find("a","x").orElseThrow().boundUserId());
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void invalidTokensStillOccupyCapacityAndGlobalClaim(String mode) throws Exception {
        var f=fixture(mode);bind(f,"a","x","1001","ACTIVE","a",null);bind(f,"a","x","1001","UNUSABLE","a",null);bind(f,"a","y","1002","ACTIVE","a",null);
        assertThrows(BizException.class,()->bind(f,"a","z","1003","ACTIVE","a",null));
        assertThrows(BizException.class,()->bind(f,"b","other","1001","ACTIVE","b",null));
        bind(f,"a","x","1001","UNBOUND","a",null);
        assertThrows(BizException.class,()->bind(f,"b","other","1001","ACTIVE","b",null)); // Release is not a cross-workspace handover.
        assertThrows(BizException.class,()->f.bindings.executionScope("a","x","a"));
        bind(f,"a","x","1001","ACTIVE","a",null);
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void concurrentCrossTenantBindHasExactlyOneWinner(String mode) throws Exception {
        var f=fixture(mode);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Boolean>>();for(String tenant:List.of("a","b"))tasks.add(pool.submit(()->{start.await();try{bind(f,tenant,tenant+"account","1001","ACTIVE",tenant,null);return true;}catch(RuntimeException ex){return false;}}));
            start.countDown();int wins=0;for(var task:tasks)if(task.get(20,TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM ad_account_claim",Integer.class));
        }
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void taskSnapshotCannotSurviveMembershipOrBindingVersionChange(String mode) throws Exception {
        var f=fixture(mode);bind(f,"a","x","1001","ACTIVE","a",null);var scope=f.bindings.executionScope("a","x","a");
        f.jdbc.update("UPDATE tenant_member SET permission_version=permission_version+1 WHERE tenant_id='a'");
        assertThrows(BizException.class,()->f.bindings.validate(scope));
        f.jdbc.update("UPDATE tenant_member SET status='REMOVED' WHERE tenant_id='a'");
        assertThrows(BizException.class,()->f.bindings.executionScope("a","x",null));
        assertThrows(BizException.class,()->new WorkspaceAccess(f.jdbc).member("a","a"));
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void walletIdempotencyPartialSettlementAndRefundAreTenantScoped(String mode) throws Exception {
        var f=fixture(mode);var p=f.points;
        f.tx.executeWithoutResult(s->{p.credit("a","payment",new BigDecimal("100"));p.credit("a","payment",new BigDecimal("100"));p.reserve("a","job",null,"a",new BigDecimal("60"));p.reserve("a","job",null,"a",new BigDecimal("60"));});
        assertEquals(0,new BigDecimal("40").compareTo(p.balance("a").available()));
        assertThrows(BizException.class,()->f.tx.executeWithoutResult(s->p.reserve("a","job",null,"a",new BigDecimal("61"))));
        f.tx.executeWithoutResult(s->{p.settle("a","job",new BigDecimal("35"));p.settle("a","job",new BigDecimal("35"));});
        assertEquals(0,new BigDecimal("65").compareTo(p.balance("a").available()));
        f.tx.executeWithoutResult(s->{p.refund("a","job");p.refund("a","job");});
        assertEquals(0,new BigDecimal("100").compareTo(p.balance("a").available()));assertEquals(0,p.balance("a").frozen().signum());
        assertEquals(0,p.balance("b").available().signum());assertTrue(p.ledger("b",1,10).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"MySQL","PostgreSQL"})
    void concurrentReservationsCannotOverdrawSharedWallet(String mode) throws Exception {
        var f=fixture(mode);f.tx.executeWithoutResult(s->f.points.credit("a","payment",new BigDecimal("100")));var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<Boolean>>();for(String key:List.of("x","y"))futures.add(pool.submit(()->{start.await();try{f.tx.executeWithoutResult(s->f.points.reserve("a",key,null,"a",new BigDecimal("70")));return true;}catch(BizException ex){return false;}}));
            start.countDown();int wins=0;for(var future:futures)if(future.get(20,TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);assertEquals(0,new BigDecimal("30").compareTo(f.points.balance("a").available()));
            assertEquals(0,new BigDecimal("70").compareTo(f.points.balance("a").frozen()));
        }
    }
}
