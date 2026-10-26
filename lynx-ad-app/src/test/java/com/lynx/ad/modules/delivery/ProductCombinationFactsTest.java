package com.lynx.ad.modules.delivery;
import static org.junit.jupiter.api.Assertions.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.ProductCombinationFactsStore;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
class ProductCombinationFactsTest {
    @Test void goalsAreExistingConfiguredTargetsScopedToTenantAccountAwemeAndPublishedProducts(){
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE ad_plan(id VARCHAR(64),tenant_id VARCHAR(64),oauth_account_id VARCHAR(64),aweme_id VARCHAR(64),qc_plan_id VARCHAR(64),status VARCHAR(20),roi2_goal DECIMAL(18,4))");
        jdbc.execute("CREATE TABLE ad_plan_creative(ad_plan_id VARCHAR(64),product_id VARCHAR(64))");
        for(var row:List.of(new String[]{"one","t","a","w","1","PAUSED","4.2"},new String[]{"two","t","a","w","2","DELIVERING","4.8"},new String[]{"other-tenant","other","a","w","3","PAUSED","99"},new String[]{"other-account","t","other","w","4","PAUSED","99"},new String[]{"other-aweme","t","a","other","5","PAUSED","99"},new String[]{"draft","t","a","w",null,"DRAFT","99"})){
            jdbc.update("INSERT INTO ad_plan VALUES(?,?,?,?,?,?,?)",(Object[])row);jdbc.update("INSERT INTO ad_plan_creative VALUES(?,?)",row[0],"100");
        }
        var result=new ProductCombinationFactsStore(jdbc).goals("t","a","w",List.of("100","101"));assertEquals(1,result.size());
        assertEquals(0,new BigDecimal("4.2").compareTo(result.get("100").min()));assertEquals(0,new BigDecimal("4.8").compareTo(result.get("100").max()));assertFalse(result.containsKey("101"));
    }
}
