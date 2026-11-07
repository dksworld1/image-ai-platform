package com.imageaiplatform.auth.sample;

import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 권한 회수와 삭제가 동일한 DB 행 잠금을 사용하는 예시입니다.
 * sample_* 테이블은 테스트에서만 생성하며 운영 스키마·빈에 등록하지 않습니다.
 * username은 인증된 호출부가 전달한다고 가정합니다.
 */
public class SampleRevocationService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SampleRevocationService(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public void delete(String username, Long itemId) {
        transaction.executeWithoutResult(status -> {
            if (!Boolean.TRUE.equals(lockPermission(username))) {
                throw new AccessDeniedException("Sample delete permission is not granted.");
            }
            if (jdbc.update("DELETE FROM sample_item WHERE id = ? AND owner = ?", itemId, username) != 1) {
                throw new IllegalStateException("Sample item is unavailable.");
            }
            jdbc.update("INSERT INTO sample_action (action) VALUES ('DELETED')");
        });
    }

    public void revoke(String username) {
        transaction.executeWithoutResult(status -> {
            lockPermission(username);
            jdbc.update("UPDATE sample_auth_guard SET can_delete = FALSE WHERE username = ?", username);
            jdbc.update("INSERT INTO sample_action (action) VALUES ('REVOKED')");
        });
    }

    private Boolean lockPermission(String username) {
        try {
            return jdbc.queryForObject(
                    "SELECT can_delete FROM sample_auth_guard WHERE username = ? FOR UPDATE", Boolean.class, username);
        } catch (EmptyResultDataAccessException exception) {
            throw new AccessDeniedException("Sample permission is not registered.");
        }
    }
}
