-- 로컬 개발 전용 초기 데이터. admin 비밀번호의 BCrypt 해시는 비운영 전용입니다.
-- 반복 적용 시 아래 코드의 계정·역할 설정을 이 값으로 맞춥니다.
MERGE INTO auth_user (username, password_hash, enabled) KEY (username)
VALUES ('admin', '$2a$10$Tosl6ImcS9TpyjewydOvWeI5BBnGgO6ykUWscFrQxq783EbBUmcJ.', TRUE);

MERGE INTO auth_role (role_code, display_name, description, system_role) KEY (role_code)
VALUES ('admin', '관리자', '로컬 개발용 전체 권한 관리자', TRUE);

INSERT INTO auth_user_role (username, role_code)
SELECT 'admin', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM auth_user_role WHERE username = 'admin' AND role_code = 'admin');

-- 와일드카드나 역할명 우회 없이 현재 정의된 모든 권한을 연결합니다.
-- 현재 코드가 사용하는 관리 권한은 공통 seed의 ui:screen:manage, ui:menu:manage입니다.
INSERT INTO auth_role_permission (role_code, permission_code)
SELECT 'admin', permission.permission_code
FROM auth_permission permission
WHERE NOT EXISTS (
    SELECT 1 FROM auth_role_permission assigned
    WHERE assigned.role_code = 'admin' AND assigned.permission_code = permission.permission_code
);
