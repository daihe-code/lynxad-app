package com.lynx.ad.modules.catalog.application;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice;
import com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore;
import com.lynx.ad.modules.catalog.infrastructure.persistence.ProductPriceStore.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

@EnabledIfEnvironmentVariable(named="LYNXAD_PRICES_TEST_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/lynxad_prices_verify_[a-zA-Z0-9_]+.*")
class ProductPriceStoreIT {
    static JdbcTemplate jdbc; static ProductPriceStore store;
    static Scope scope=new Scope("tenant-a","account-a","123","456");
    static Ref ref=new Ref("3841023363610968621",null);
    @BeforeAll static void setup() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("LYNXAD_PRICES_TEST_URL"),"root",System.getenv("LYNXAD_PRICES_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE product(id VARCHAR(64) PRIMARY KEY,price DECIMAL(12,2),user_price DECIMAL(12,2))");
        jdbc.update("INSERT INTO product VALUES ('automatic',20,20),('manual',20,18),('manual-without-platform',NULL,15)");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration-mysql/V27__product_prices.sql"));}
        store=new ProductPriceStore(jdbc,new ObjectMapper().findAndRegisterModules());
    }
    @Test void durablePriceRoundTripsAndCannotCrossTenantAccountAwemeOrChannel() {
        var price=new PlatformProductPrice(new BigDecimal("17.89"),new BigDecimal("19.89"),new BigDecimal("20.00"),"QIANCHUAN_AVAILABLE",Instant.parse("2026-10-11T14:00:00Z"),false);
        store.save(scope,ref,price);assertEquals(price,store.load(scope,List.of(ref)).get(ref));
        assertTrue(store.load(new Scope("tenant-b","account-a","123","456"),List.of(ref)).isEmpty());
        assertTrue(store.load(new Scope("tenant-a","account-b","123","456"),List.of(ref)).isEmpty());
        assertTrue(store.load(new Scope("tenant-a","account-a","123","789"),List.of(ref)).isEmpty());
        assertTrue(store.load(scope,List.of(new Ref(ref.id(),"channel-b"))).isEmpty());
        var next=new PlatformProductPrice(new BigDecimal("18.89"),new BigDecimal("18.89"),null,price.source(),price.fetchedAt().plusSeconds(60),false);
        store.save(scope,ref,next);assertEquals(next,new ProductPriceStore(jdbc,new ObjectMapper().findAndRegisterModules()).load(scope,List.of(ref)).get(ref));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM catalog_product_price",Integer.class));
    }
    @Test void migrationPreservesAllSavedCalculationValuesIncludingValuesEqualToPlatformPrice() {
        assertTrue(jdbc.queryForObject("SELECT user_price_manual FROM product WHERE id='automatic'",Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT user_price_manual FROM product WHERE id='manual'",Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT user_price_manual FROM product WHERE id='manual-without-platform'",Boolean.class));
        assertEquals(new BigDecimal("18.00"),jdbc.queryForObject("SELECT user_price FROM product WHERE id='manual'",BigDecimal.class));
    }
}
