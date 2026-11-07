INSERT INTO auth_permission (permission_code, display_name, description)
SELECT 'auth:user:manage', '사용자 관리', '사용자 역할과 활성 상태 관리'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'auth:user:manage');

INSERT INTO auth_role_permission (role_code, permission_code)
SELECT 'ADMIN', 'auth:user:manage'
WHERE NOT EXISTS (
    SELECT 1 FROM auth_role_permission WHERE role_code = 'ADMIN' AND permission_code = 'auth:user:manage'
);
