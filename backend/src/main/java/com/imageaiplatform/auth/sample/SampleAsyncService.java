package com.imageaiplatform.auth.sample;

import java.util.concurrent.CompletableFuture;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;

public class SampleAsyncService {
    private final AsyncTaskExecutor securedExecutor;
    private final SampleProtectedService protectedService;

    public SampleAsyncService(AsyncTaskExecutor securedExecutor, SampleProtectedService protectedService) {
        this.securedExecutor = securedExecutor;
        this.protectedService = protectedService;
    }

    @Async("sampleSecuredExecutor")
    public CompletableFuture<String> readAsync() {
        // 별도 빈의 프록시를 통해 서비스 권한 검사를 수행합니다.
        return CompletableFuture.completedFuture(protectedService.read());
    }

    public CompletableFuture<String> readNestedAsync() {
        // 두 단계 모두 같은 보안 실행기를 사용하고, 작업 안에서 join으로 스레드를 막지 않습니다.
        return CompletableFuture.supplyAsync(
                () -> CompletableFuture.supplyAsync(protectedService::read, securedExecutor), securedExecutor)
                .thenCompose(result -> result);
    }
}
