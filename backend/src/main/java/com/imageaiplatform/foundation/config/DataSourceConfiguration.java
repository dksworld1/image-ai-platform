package com.imageaiplatform.foundation.config;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MyBatisConfigurationProperties.class)
public class DataSourceConfiguration {
    @Bean
    @ConditionalOnProperty("datasource.audit.url")
    @ConfigurationProperties("datasource.audit")
    DataSourceProperties auditDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConditionalOnProperty("datasource.audit.url")
    @ConfigurationProperties("datasource.audit.hikari")
    HikariDataSource auditDataSource(
            @Qualifier("auditDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("datasource.app")
    DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("datasource.app.hikari")
    HikariDataSource appDataSource(
            @Qualifier("appDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("datasource.catalog")
    DataSourceProperties catalogDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("datasource.catalog.hikari")
    HikariDataSource catalogDataSource(
            @Qualifier("catalogDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("datasource.archive")
    DataSourceProperties archiveDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("datasource.archive.hikari")
    HikariDataSource archiveDataSource(
            @Qualifier("archiveDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    SqlSessionFactory appSqlSessionFactory(
            @Qualifier("appDataSource") DataSource dataSource,
            MyBatisConfigurationProperties myBatisProperties,
            ResourceLoader resourceLoader)
            throws Exception {
        return createSqlSessionFactory(dataSource, myBatisProperties, resourceLoader);
    }

    @Bean
    SqlSessionFactory catalogSqlSessionFactory(
            @Qualifier("catalogDataSource") DataSource dataSource,
            MyBatisConfigurationProperties myBatisProperties,
            ResourceLoader resourceLoader)
            throws Exception {
        return createSqlSessionFactory(dataSource, myBatisProperties, resourceLoader);
    }

    @Bean
    SqlSessionFactory archiveSqlSessionFactory(
            @Qualifier("archiveDataSource") DataSource dataSource,
            MyBatisConfigurationProperties myBatisProperties,
            ResourceLoader resourceLoader)
            throws Exception {
        return createSqlSessionFactory(dataSource, myBatisProperties, resourceLoader);
    }

    @Bean
    SqlSessionTemplate appSqlSessionTemplate(
            @Qualifier("appSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    SqlSessionTemplate catalogSqlSessionTemplate(
            @Qualifier("catalogSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    SqlSessionTemplate archiveSqlSessionTemplate(
            @Qualifier("archiveSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    PlatformTransactionManager appTransactionManager(
            @Qualifier("appDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    PlatformTransactionManager catalogTransactionManager(
            @Qualifier("catalogDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    PlatformTransactionManager archiveTransactionManager(
            @Qualifier("archiveDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private SqlSessionFactory createSqlSessionFactory(
            DataSource dataSource,
            MyBatisConfigurationProperties myBatisProperties,
            ResourceLoader resourceLoader) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfigLocation(resourceLoader.getResource(myBatisProperties.getConfigLocation()));
        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        Resource[] mapperResources = resourcePatternResolver.getResources(myBatisProperties.getMapperLocations());
        factoryBean.setMapperLocations(mapperResources);
        SqlSessionFactory factory = factoryBean.getObject();
        if (factory == null) {
            throw new IllegalStateException("Could not create a MyBatis SqlSessionFactory.");
        }
        return factory;
    }

}

