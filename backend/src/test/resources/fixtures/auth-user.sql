INSERT INTO auth_user (username, password_hash, enabled)
SELECT 'test-user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM auth_user
    WHERE username = 'test-user'
);
