package com.imageaiplatform.auth.sample;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * 샘플 전용 설정입니다. @Configuration을 붙이지 않아 운영 컴포넌트 스캔에서 등록되지 않습니다.
 * 테스트 등에서 AnnotationConfigApplicationContext에 이 클래스를 직접 등록해 사용합니다.
 */
@EnableMethodSecurity
@EnableAsync
public class SampleConfiguration {
    @Bean
    SampleAccounts sampleAccounts() {
        return new SampleAccounts();
    }

    @Bean
    SamplePermissionGuard samplePermissionGuard(SampleAccounts accounts) {
        return new SamplePermissionGuard(accounts.authorizationService());
    }

    @Bean
    ThreadPoolTaskExecutor sampleRawExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("auth-sample-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    @Bean
    AsyncTaskExecutor sampleSecuredExecutor(@Qualifier("sampleRawExecutor") ThreadPoolTaskExecutor executor) {
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Bean
    SampleProtectedService sampleProtectedService() {
        return new SampleProtectedService();
    }

    @Bean
    SampleAsyncService sampleAsyncService(@Qualifier("sampleSecuredExecutor") AsyncTaskExecutor executor,
            SampleProtectedService service) {
        return new SampleAsyncService(executor, service);
    }
}
