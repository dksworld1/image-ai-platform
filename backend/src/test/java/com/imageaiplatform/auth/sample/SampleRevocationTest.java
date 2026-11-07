package com.imageaiplatform.auth.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;

class SampleRevocationTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private SampleRevocationService service;
    private ExecutorService workers;

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:sample-revocation-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new SampleRevocationService(dataSource);
        workers = Executors.newFixedThreadPool(2);
        jdbc.execute("CREATE TABLE sample_auth_guard (username VARCHAR(100) PRIMARY KEY, can_delete BOOLEAN NOT NULL)");
        jdbc.execute("CREATE TABLE sample_item (id BIGINT PRIMARY KEY, owner VARCHAR(100) NOT NULL)");
        jdbc.execute("CREATE TABLE sample_action (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, action VARCHAR(20) NOT NULL)");
        jdbc.update("INSERT INTO sample_auth_guard VALUES ('sample-user', TRUE)");
        jdbc.update("INSERT INTO sample_item VALUES (1, 'sample-user')");
    }

    @AfterEach
    void cleanup() throws Exception {
        workers.shutdownNow();
        assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void operationFirstCommitsBeforeRevocation() throws Exception {
        var operationReady = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        Future<?> operation = workers.submit(() -> transaction.executeWithoutResult(status -> {
            service.delete("sample-user", 1L);
            operationReady.countDown();
            await(allowCommit);
        }));
        try {
            assertThat(operationReady.await(5, TimeUnit.SECONDS)).isTrue();
            var revocationStarted = new CountDownLatch(1);
            Future<?> revocation = workers.submit(() -> {
                revocationStarted.countDown();
                service.revoke("sample-user");
            });
            assertThat(revocationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> revocation.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            allowCommit.countDown();
            operation.get(5, TimeUnit.SECONDS);
            revocation.get(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample_item", Integer.class)).isZero();
            assertThat(jdbc.queryForList("SELECT action FROM sample_action ORDER BY id", String.class))
                    .containsExactly("DELETED", "REVOKED");
        } finally {
            allowCommit.countDown();
        }
    }

    @Test
    void revocationFirstMakesWaitingOperationFailWithoutDeletion() throws Exception {
        var revocationReady = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        Future<?> revocation = workers.submit(() -> transaction.executeWithoutResult(status -> {
            service.revoke("sample-user");
            revocationReady.countDown();
            await(allowCommit);
        }));
        try {
            assertThat(revocationReady.await(5, TimeUnit.SECONDS)).isTrue();
            var operationStarted = new CountDownLatch(1);
            Future<?> operation = workers.submit(() -> {
                operationStarted.countDown();
                service.delete("sample-user", 1L);
            });
            assertThat(operationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> operation.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            allowCommit.countDown();
            revocation.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> operation.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(AccessDeniedException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample_item", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT action FROM sample_action ORDER BY id", String.class))
                    .containsExactly("REVOKED");
        } finally {
            allowCommit.countDown();
        }
    }

    @Test
    void failedOperationRollsBackDeletionAndReleasesLock() throws Exception {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.delete("sample-user", 1L);
            throw new IllegalStateException("Sample work failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample_item", Integer.class)).isEqualTo(1);
        workers.submit(() -> service.revoke("sample-user")).get(5, TimeUnit.SECONDS);
        assertThat(jdbc.queryForList("SELECT action FROM sample_action ORDER BY id", String.class))
                .containsExactly("REVOKED");
    }

    @Test
    void failedRevocationRollsBackPermissionChange() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.revoke("sample-user");
            throw new IllegalStateException("Sample revocation failed");
        })).isInstanceOf(IllegalStateException.class);
        service.delete("sample-user", 1L);
        assertThat(jdbc.queryForList("SELECT action FROM sample_action ORDER BY id", String.class))
                .containsExactly("DELETED");
    }

    @Test
    void missingPermissionAndWrongOwnerCannotDeleteItem() {
        assertThatThrownBy(() -> service.delete("unknown-user", 1L)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("INSERT INTO sample_auth_guard VALUES ('other-user', TRUE)");
        assertThatThrownBy(() -> service.delete("other-user", 1L)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample_item", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample_action", Integer.class)).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Sample commit release timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
