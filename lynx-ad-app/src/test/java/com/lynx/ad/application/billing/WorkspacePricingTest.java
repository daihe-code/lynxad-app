package com.lynx.ad.application.billing;

import com.lynx.ad.modules.billing.application.WorkspacePricing;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkspacePricingTest {
    @ParameterizedTest
    @CsvSource({
            "9900, 1630", "100000, 16466", "499999, 82333",
            "500000, 99000", "999999, 197999",
            "1000000, 214666", "1999999, 429333", "2000000, 462666"
    })
    void appliesOneMarginToTheFullAmountAndRoundsPointsDown(int amountFen, long points) {
        var quote = new WorkspacePricing().rechargeQuote(amountFen);
        assertEquals(amountFen, quote.priceFen());
        assertEquals(points, quote.points());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void updatesCatalogPricesWithoutTouchingExistingOrders(String mode) {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:pricing_" + UUID.randomUUID()
                + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE billing_plan(code VARCHAR(32) PRIMARY KEY, list_price_fen INT)");
        jdbc.execute("INSERT INTO billing_plan VALUES ('BASIC', 29900), ('PROFESSIONAL', 79900), ('FLAGSHIP', 299900)");
        jdbc.execute("CREATE TABLE billing_order(list_price_fen INT)");
        jdbc.execute("INSERT INTO billing_order VALUES (29900)");
        String dir = mode.equals("MySQL") ? "migration-mysql" : "migration";
        new ResourceDatabasePopulator(new ClassPathResource("db/" + dir + "/V60__seat_and_credit_pricing.sql")).execute(ds);
        assertEquals(List.of(19900, 709900, 2299900),
                jdbc.queryForList("SELECT list_price_fen FROM billing_plan ORDER BY list_price_fen", Integer.class));
        assertEquals(29900, jdbc.queryForObject("SELECT list_price_fen FROM billing_order", Integer.class));
    }
}
