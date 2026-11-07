package com.imageaiplatform.auth.config;

import com.imageaiplatform.auth.service.AuthorizationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
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
