package com.feng.system.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class Sub2apiDataSourceConfig {

    @Bean(name = "sub2apiDataSource")
    public DataSource sub2apiDataSource(
            @Value("${sub2api.datasource.url}") String url,
            @Value("${sub2api.datasource.username}") String username,
            @Value("${sub2api.datasource.password}") String password,
            @Value("${sub2api.datasource.maximum-pool-size:10}") int maximumPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setPoolName("sub2api-billing");
        return new HikariDataSource(config);
    }

    @Bean(name = "sub2apiJdbcTemplate")
    public JdbcTemplate sub2apiJdbcTemplate(@Qualifier("sub2apiDataSource") DataSource sub2apiDataSource) {
        return new JdbcTemplate(sub2apiDataSource);
    }

    @Bean(name = "sub2apiTransactionManager")
    public PlatformTransactionManager sub2apiTransactionManager(@Qualifier("sub2apiDataSource") DataSource sub2apiDataSource) {
        return new DataSourceTransactionManager(sub2apiDataSource);
    }
}
