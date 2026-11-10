-- 로컬 개발 전용 초기 데이터. admin 비밀번호의 BCrypt 해시는 비운영 전용입니다.
-- 반복 적용 시 생성 정보는 보존하고 수정 정보만 갱신합니다. 시스템 작업자 ID는 0입니다.
MERGE INTO auth_user target
USING (VALUES (CAST(RANDOM_UUID() AS VARCHAR), 'admin', '로컬 관리자',
    '$2a$10$Tosl6ImcS9TpyjewydOvWeI5BBnGgO6ykUWscFrQxq783EbBUmcJ.')) source(user_key, user_id, username, password_hash)
ON target.user_id = source.user_id
WHEN MATCHED THEN UPDATE SET password_hash = source.password_hash, enabled = TRUE,
    username = COALESCE(target.username, source.username), updated_at = CURRENT_TIMESTAMP, updated_by = '0'
WHEN NOT MATCHED THEN INSERT (user_key, user_id, username, password_hash, enabled, created_at, updated_at, created_by, updated_by)
VALUES (source.user_key, source.user_id, source.username, source.password_hash, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0');

MERGE INTO auth_role target
USING (VALUES ('admin', '관리자', '로컬 개발용 전체 권한 관리자')) source(role_code, display_name, description)
ON target.role_code = source.role_code
WHEN MATCHED THEN UPDATE SET display_name = source.display_name, description = source.description, system_role = TRUE,
    updated_at = CURRENT_TIMESTAMP, updated_by = '0'
WHEN NOT MATCHED THEN INSERT (role_code, display_name, description, system_role, created_at, updated_at, created_by, updated_by)
VALUES (source.role_code, source.display_name, source.description, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0');

INSERT INTO auth_user_role (user_key, role_code, created_at, updated_at, created_by, updated_by)
SELECT account.user_key, 'admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM auth_user account
WHERE account.user_id = 'admin'
  AND NOT EXISTS (SELECT 1 FROM auth_user_role WHERE user_key = account.user_key AND role_code = 'admin');

-- 와일드카드나 역할명 우회 없이 현재 정의된 모든 권한을 연결합니다.
INSERT INTO auth_role_permission (role_code, permission_code, created_at, updated_at, created_by, updated_by)
SELECT 'admin', permission.permission_code, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM auth_permission permission
WHERE NOT EXISTS (
    SELECT 1 FROM auth_role_permission assigned
    WHERE assigned.role_code = 'admin' AND assigned.permission_code = permission.permission_code
);
