INSERT INTO auth_user (user_key, user_id, username, password_hash, enabled, created_at, updated_at, created_by, updated_by)
SELECT 'test-key', 'test-user', '테스트 사용자', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
WHERE NOT EXISTS (
    SELECT 1
    FROM auth_user
    WHERE user_key = 'test-key'
);
