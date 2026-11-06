package com.imageaiplatform.auth.config;

import com.imageaiplatform.auth.service.AuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.database-migration-only", havingValue = "false", matchIfMissing = true)
public class ApiAuthorizationConfiguration implements WebMvcConfigurer {
    private final AuthorizationService authorizationService;

    public ApiAuthorizationConfiguration(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiAuthorizationInterceptor(authorizationService));
    }
}
