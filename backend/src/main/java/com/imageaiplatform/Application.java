package com.imageaiplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.Arrays;

@SpringBootApplication(exclude = MybatisAutoConfiguration.class)
public class Application {
    public static void main(String[] args) {
        boolean migrationOnly = Arrays.asList(args).contains("--app.database-migration-only=true");
        SpringApplication application = new SpringApplication(Application.class);
        if (migrationOnly) {
            application.setWebApplicationType(WebApplicationType.NONE);
            try (ConfigurableApplicationContext ignored = application.run(args)) {
                // Flyway runs during context initialization; closing the context ends this one-shot command.
            }
            return;
        }
        application.run(args);
    }
}
