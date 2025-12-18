package com.exam.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration // 1. 确保这个注解没有被注释掉
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "entityManagerFactorySqlServer",
        transactionManagerRef = "transactionManagerSqlServer", // 2. 这里引用了下面定义的 Bean
        basePackages = {"com.exam.backend.repository.sqlserver"}
)
public class SqlServerConfig {

    @Bean(name = "sqlServerDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.sqlserver")
    public DataSource sqlServerDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "entityManagerFactorySqlServer")
    public LocalContainerEntityManagerFactoryBean entityManagerFactorySqlServer(
            EntityManagerFactoryBuilder builder,
            @Qualifier("sqlServerDataSource") DataSource dataSource) {

        // 如果你是用 MySQL 冒充 SQL Server，这里的方言要改成 MySQLDialect
        // 如果是真实 SQL Server，用 org.hibernate.dialect.SQLServerDialect
        Map<String, Object> properties = new HashMap<>();
        // 真实 SQL Server 用这个：
        properties.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
        properties.put("hibernate.hbm2ddl.auto", "update");

        return builder
                .dataSource(dataSource)
                .packages("com.exam.backend.entity")
                .persistenceUnit("sqlServerPersistenceUnit")
                .properties(properties)
                .build();
    }

    // === 3. 你之前可能缺的就是这一段 ===
    @Bean(name = "transactionManagerSqlServer")
    public PlatformTransactionManager transactionManagerSqlServer(
            @Qualifier("entityManagerFactorySqlServer") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}