package com.imageaiplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.Arrays;
import com.imageaiplatform.auth.service.AdminBootstrapService;

@SpringBootApplication(exclude = MybatisAutoConfiguration.class)
public class Application {
    public static void main(String[] args) {
        boolean migrationOnly = Arrays.asList(args).contains("--app.database-migration-only=true");
        boolean bootstrapOnly = Arrays.asList(args).contains("--app.admin-bootstrap-only=true");
        SpringApplication application = new SpringApplication(Application.class);
        if (bootstrapOnly) {
            args = Arrays.copyOf(args, args.length + 1);
            args[args.length - 1] = "--app.database-migration-only=true";
        }
        if (migrationOnly || bootstrapOnly) {
            application.setWebApplicationType(WebApplicationType.NONE);
            try (ConfigurableApplicationContext context = application.run(args)) {
                if (bootstrapOnly) {
                    context.getBean(AdminBootstrapService.class).create(
                            context.getEnvironment().getRequiredProperty("INITIAL_ADMIN_USERNAME"),
                            context.getEnvironment().getRequiredProperty("INITIAL_ADMIN_PASSWORD"));
                }
                // Flyway runs during context initialization; closing the context ends this one-shot command.
            }
            return;
        }
        application.run(args);
    }
}
