package com.imageaiplatform.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration(proxyBeanMethods = false)
@Profile("!prod & (local | test)")
public class FoundationSchemaInitializationConfiguration {
    @Bean
    InitializingBean catalogSchemaInitializer(@Qualifier("catalogDataSource") DataSource dataSource) {
        return () -> initializeSchema(dataSource);
    }

    @Bean
    InitializingBean archiveSchemaInitializer(@Qualifier("archiveDataSource") DataSource dataSource) {
        return () -> initializeSchema(dataSource);
    }

    private void initializeSchema(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema-foundation.sql"));
        populator.execute(dataSource);
    }
}
