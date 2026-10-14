package com.lynx.ad.modules.billing.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.lynx.ad.modules.billing.domain.port.SubscriptionRepository;
import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Uses the production mapper and repository against an isolated integration-test schema. */
public final class SubscriptionPersistenceFixture {
    private SubscriptionPersistenceFixture() {}
    public static SubscriptionRepository create(DataSource source)throws Exception {
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(BillingSubscriptionMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(configuration);
        var session=new SqlSessionTemplate(factory.getObject());
        return new MpSubscriptionRepository(session.getMapper(BillingSubscriptionMapper.class),new JdbcTemplate(source));
    }
}
