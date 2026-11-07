package com.imageaiplatform.auth.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthSampleTest {
    private AnnotationConfigApplicationContext context;
    private SampleAccounts accounts;
    private SampleProtectedService protectedService;
    private SampleAsyncService asyncService;
    private ThreadPoolTaskExecutor rawExecutor;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(SampleConfiguration.class);
        accounts = context.getBean(SampleAccounts.class);
        protectedService = context.getBean(SampleProtectedService.class);
        asyncService = context.getBean(SampleAsyncService.class);
        rawExecutor = context.getBean("sampleRawExecutor", ThreadPoolTaskExecutor.class);
        accounts.put("sample-user", true, Set.of("sample:item:read"));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void methodSecurityChecksCurrentMockPermissions() {
        authenticate("sample-user");
        assertThat(protectedService.read()).isEqualTo("sample-user");
        accounts.put("sample-user", true, Set.of());
        assertThatThrownBy(() -> protectedService.read()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsMissingAuthenticationAndDisabledAccount() {
        assertThatThrownBy(() -> protectedService.read()).isInstanceOf(AuthenticationException.class);
        authenticate("sample-user");
        accounts.put("sample-user", false, Set.of("sample:item:read"));
        assertThatThrownBy(() -> protectedService.read()).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void asyncAnnotationTransfersAuthenticationAndCleansWorker() throws Exception {
        authenticate("sample-user");
        var result = asyncService.readAsync();
        SecurityContextHolder.clearContext();
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("sample-user");
        assertWorkerHasNoAuthentication();
    }

    @Test
    void nestedAsyncTransfersEachUserWithoutLeakingToReusedThread() throws Exception {
        accounts.put("other-user", true, Set.of("sample:item:read"));
        authenticate("sample-user");
        var first = asyncService.readNestedAsync();
        authenticate("other-user");
        var second = asyncService.readNestedAsync();
        SecurityContextHolder.clearContext();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("sample-user");
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("other-user");
        assertWorkerHasNoAuthentication();
    }

    @Test
    void unauthenticatedAsyncSubmissionDoesNotInventAuthentication() throws Exception {
        var result = asyncService.readNestedAsync();
        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AuthenticationException.class);
        assertWorkerHasNoAuthentication();
    }

    @Test
    void queuedWorkRechecksPermissionsAndCleansContextAfterFailure() throws Exception {
        var workerEntered = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var blocker = rawExecutor.submit(() -> {
            workerEntered.countDown();
            if (!releaseWorker.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Sample worker release timed out");
            }
            return null;
        });
        try {
            assertThat(workerEntered.await(5, TimeUnit.SECONDS)).isTrue();
            authenticate("sample-user");
            var result = asyncService.readNestedAsync();
            accounts.put("sample-user", true, Set.of());
            SecurityContextHolder.clearContext();
            releaseWorker.countDown();
            blocker.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(AccessDeniedException.class);
            assertWorkerHasNoAuthentication();
        } finally {
            releaseWorker.countDown();
        }
    }

    private void authenticate(String username) {
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(username, null, Set.of()));
        SecurityContextHolder.setContext(securityContext);
    }

    private void assertWorkerHasNoAuthentication() throws Exception {
        assertThat(rawExecutor.submit(() -> SecurityContextHolder.getContext().getAuthentication())
                .get(5, TimeUnit.SECONDS)).isNull();
    }
}
