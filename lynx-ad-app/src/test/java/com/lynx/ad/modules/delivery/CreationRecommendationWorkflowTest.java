package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class CreationRecommendationWorkflowTest {
    final ObjectMapper json=new ObjectMapper();final TenantId tenant=new TenantId("tenant");
    CreationStore store,otherStore;CreationWorkflowService first,second;CreationQianchuanGateway gateway;
    void database(String mode)throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:recommendation_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000","sa","");var manager=new DataSourceTransactionManager(ds);
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V18__creation_workflow.sql"));}
        store=new CreationStore(new JdbcTemplate(ds),json,manager);otherStore=new CreationStore(new JdbcTemplate(ds),json,manager);gateway=mock(CreationQianchuanGateway.class);
        first=workflow(store,manager);second=workflow(otherStore,manager);
        var s=new CreationState();s.tenantId="tenant";s.userId="user";s.planId="plan";s.accountId="account";s.awemeId="aweme";s.name="草稿";s.budget=new BigDecimal("400");
        s.products.add(product("123"));store.insert(s);
        when(gateway.recommendations(any(),any(),any(),any())).thenAnswer(c->result(true));
    }
    CreationState.Product product(String id){return new CreationState.Product(id,id,"商品",null,null,null,null,null,List.of(),List.of());}
    CreationWorkflowService workflow(CreationStore db,DataSourceTransactionManager manager){return new CreationWorkflowService(db,null,null,gateway,null,json,null,null,null,null,mock(BillingTrialService.class),manager);}
    com.fasterxml.jackson.databind.node.ObjectNode result(boolean full)throws Exception {
        var r=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("{\"accountId\":\"account\",\"awemeId\":\"aweme\",\"productIds\":[\"123\"],\"budget\":{\"budget\":50},\"roi\":{\"ecp_roi2_goal\":4.82}}");r.put("fetchedAt",Instant.now().toString());
        if(full)((com.fasterxml.jackson.databind.node.ObjectNode)r.path("budget")).putArray("estimate_trans_cost_list").addObject().put("budget",50).put("estimate_roi",0.38).put("estimate_convert",8);
        return r;
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void concurrentPageVisitsAndARestartReuseTheSamePersistedRecommendation(String mode)throws Exception {
        database(mode);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            Callable<CreationState> call=()->{gate.await();return first.recommendations(tenant,"plan");};var a=pool.submit(call);var b=pool.submit(call);gate.countDown();
            assertEquals(50,a.get(10,TimeUnit.SECONDS).recommendations.path("budget").path("budget").asInt());assertEquals(50,b.get(10,TimeUnit.SECONDS).recommendations.path("budget").path("budget").asInt());
            second.recommendations(tenant,"plan");verify(gateway,times(1)).recommendations(any(),any(),any(),any());assertEquals(new BigDecimal("400"),otherStore.get("tenant","plan").budget);assertEquals(2,otherStore.get("tenant","plan").revision);
        }finally{pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void emptyForecastIsRetriedAfterTheCooldownButNeverOnEveryModeSwitch(String mode)throws Exception {
        database(mode);when(gateway.recommendations(any(),any(),any(),any())).thenReturn(result(false),result(true));
        first.recommendations(tenant,"plan");second.recommendations(tenant,"plan");verify(gateway,times(1)).recommendations(any(),any(),any(),any());
        store.mutate("tenant","plan",null,s->((com.fasterxml.jackson.databind.node.ObjectNode)s.recommendations).put("fetchedAt",Instant.now().minusSeconds(31).toString()));
        var restored=second.recommendations(tenant,"plan");assertEquals(1,restored.recommendations.path("budget").path("estimate_trans_cost_list").size());verify(gateway,times(2)).recommendations(any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void aFailedRefreshKeepsTheBudgetWithoutStaleForecastAndDoesNotHammerThePlatform(String mode)throws Exception {
        database(mode);first.recommendations(tenant,"plan");var failure=result(false).put("budgetError","请求频率受限").put("budgetStatus","RATE_LIMITED");failure.remove("budget");when(gateway.recommendations(any(),any(),any(),any())).thenReturn(failure);
        var saved=second.recommendations(tenant,"plan",true);assertTrue(saved.recommendations.path("budgetFromCache").asBoolean());assertEquals(50,saved.recommendations.path("budget").path("budget").asInt());assertFalse(saved.recommendations.path("budget").has("estimate_trans_cost_list"));
        first.recommendations(tenant,"plan",true);verify(gateway,times(2)).recommendations(any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void anAnalysisOrBudgetRevisionDuringTheReadDoesNotDiscardRecommendationsOrOverwriteEdits(String mode)throws Exception {
        database(mode);when(gateway.recommendations(any(),any(),any(),any())).thenAnswer(c->{otherStore.mutate("tenant","plan",null,s->{s.name="并发修改";s.budget=new BigDecimal("450");s.analysisStatus="SUCCEEDED";s.changed();});return result(true);});
        var saved=first.recommendations(tenant,"plan");assertEquals("并发修改",saved.name);assertEquals(new BigDecimal("450"),saved.budget);assertEquals("SUCCEEDED",saved.analysisStatus);assertEquals(50,saved.recommendations.path("budget").path("budget").asInt());
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void aLateResponseCannotCrossAChangedSelectionOrOverwriteANewerRecommendation(String mode)throws Exception {
        database(mode);when(gateway.recommendations(any(),any(),any(),any())).thenAnswer(c->{otherStore.mutate("tenant","plan",null,s->{s.products=new ArrayList<>(List.of(product("456")));s.recommendations=null;s.changed();});return result(true);});
        assertEquals(1404,assertThrows(BizException.class,()->first.recommendations(tenant,"plan")).code());var cleared=otherStore.get("tenant","plan").recommendations;assertTrue(cleared==null||cleared.isNull());
        var newer=result(true);((com.fasterxml.jackson.databind.node.ObjectNode)newer.path("budget")).put("budget",100);newer.putArray("productIds").add("456");
        when(gateway.recommendations(any(),any(),any(),any())).thenAnswer(c->{otherStore.mutate("tenant","plan",null,s->s.recommendations=newer);return result(true);});
        var saved=first.recommendations(tenant,"plan");assertEquals(100,saved.recommendations.path("budget").path("budget").asInt());assertThrows(BizException.class,()->first.recommendations(new TenantId("other"),"plan"));
    }
}
