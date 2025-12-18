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

@Configuration // 1. 确保有这个注解
@EnableTransactionManagement
@EnableJpaRepositories(
        entityManagerFactoryRef = "entityManagerFactoryOracle",
        transactionManagerRef = "transactionManagerOracle", // 2. 引用下面的事务管理器 Bean
        basePackages = {"com.exam.backend.repository.oracle"} // 3. 扫描 oracle 包
)
public class OracleConfig {

    // === 1. 数据源配置 ===
    @Bean(name = "oracleDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.oracle")
    public DataSource oracleDataSource() {
        return DataSourceBuilder.create().build();
    }

    // === 2. 实体管理器工厂 ===
    @Bean(name = "entityManagerFactoryOracle")
    public LocalContainerEntityManagerFactoryBean entityManagerFactoryOracle(
            EntityManagerFactoryBuilder builder,
            @Qualifier("oracleDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();

        // 【关键点】方言设置
        // 如果你是在用 MySQL "冒充" Oracle (障眼法)，请使用 MySQLDialect
        // 如果你是连真实的 Oracle 数据库，请改成 "org.hibernate.dialect.OracleDialect"
        // 因为你连接的是真实 Oracle，必须用这个：
        properties.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");

        properties.put("hibernate.hbm2ddl.auto", "update");

        return builder
                .dataSource(dataSource)
                .packages("com.exam.backend.entity") // 扫描实体类
                .persistenceUnit("oraclePersistenceUnit")
                .properties(properties)
                .build();
    }

    // === 3. 事务管理器 (之前报错就是缺这个) ===
    @Bean(name = "transactionManagerOracle")
    public PlatformTransactionManager transactionManagerOracle(
            @Qualifier("entityManagerFactoryOracle") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}