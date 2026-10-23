package com.lynx.ad.modules.identity.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AwemeLabelsPersistenceTest {
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void returnedLabelsSurviveDatabaseReloadAndAreIncludedInAccountResponses(String mode)throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:aweme_labels_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE oauth_account(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),platform VARCHAR(24),advertiser_id VARCHAR(64),account_name VARCHAR(128),access_token_enc TEXT,refresh_token_enc TEXT,token_expires_at TIMESTAMP,refresh_expires_at TIMESTAMP,status VARCHAR(24),authorized_at TIMESTAMP,authorized_by_user_id VARCHAR(64),grant_id VARCHAR(64),application_id VARCHAR(64),catalog_error TEXT)");
        jdbc.execute("CREATE TABLE oauth_aweme(id VARCHAR(64) PRIMARY KEY,oauth_account_id VARCHAR(64),aweme_id VARCHAR(64),name VARCHAR(128),avatar TEXT,aweme_type VARCHAR(24),is_primary BOOLEAN)");
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/V43__aweme_authorization_labels.sql"));}
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(OauthAccountMapper.class);config.addMapper(OauthAwemeMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        var session=new SqlSessionTemplate(factory.getObject());var repository=new MpOauthAccountRepository(session.getMapper(OauthAccountMapper.class),session.getMapper(OauthAwemeMapper.class));
        var tenant=new TenantId("labels-tenant");var id=new AccountId("labels-account");
        var account=new OauthAccount(id,tenant,Platform.QIANCHUAN,new AdvertiserId("123"),"账户",new EncryptedToken("encrypted"),null,Instant.now().plusSeconds(3600),Instant.now().plusSeconds(7200),"ACTIVE",
            List.of(new Aweme(new AwemeId("111"),"官方号",null,AwemeType.AUTHORIZED,false,"已授权抖音号",List.of("OFFICIAL","AWEME_COOPERATOR"),true),new Aweme(new AwemeId("222"),"旧数据",null,AwemeType.AUTHORIZED,false,"已授权抖音号")));
        repository.save(account);
        var returned=new OauthQueryService(repository).list(tenant).getFirst().awemes();
        var official=returned.stream().filter(a->a.awemeId().equals("111")).findFirst().orElseThrow();
        assertEquals(List.of("OFFICIAL","AWEME_COOPERATOR"),official.authTypes());assertEquals(Boolean.TRUE,official.isAllowMall());assertFalse(official.isPrimary());
        var legacy=returned.stream().filter(a->a.awemeId().equals("222")).findFirst().orElseThrow();assertTrue(legacy.authTypes().isEmpty());assertNull(legacy.isAllowMall());
        assertTrue(repository.findById(new TenantId("other-tenant"),id).isEmpty());
        jdbc.execute("SHUTDOWN");
    }
}
