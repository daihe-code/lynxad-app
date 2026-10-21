package com.lynx.ad.application.tenant;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.tenant.api.WelcomeController;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.junit.jupiter.api.Assertions.*;

class WelcomeControllerTest {
    JdbcTemplate jdbc;WelcomeController controller;
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:welcome"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_user(id VARCHAR(36) PRIMARY KEY)");jdbc.execute("CREATE TABLE user_welcome_ack(user_id VARCHAR(36) PRIMARY KEY REFERENCES sys_user(id),acknowledged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        jdbc.update("INSERT INTO sys_user(id) VALUES ('old'),('new-a'),('new-b')");jdbc.update("INSERT INTO user_welcome_ack(user_id) VALUES ('old')");
        controller=new WelcomeController(jdbc,new DataSourceTransactionManager(ds));
    }
    void user(String id){TenantContext.set(new TenantPrincipal(new UserId(id),Optional.empty(),Optional.empty(),SysRole.USER));}
    @AfterEach void clean(){TenantContext.clear();}
    @Test void acknowledgementIsPersistentIdempotentAndUserScoped(){
        user("new-a");assertFalse(controller.status().data().acknowledged());controller.acknowledge();controller.acknowledge();assertTrue(controller.status().data().acknowledged());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_welcome_ack WHERE user_id='new-a'",Integer.class));
        user("new-b");assertFalse(controller.status().data().acknowledged());user("old");assertTrue(controller.status().data().acknowledged());
    }
}
