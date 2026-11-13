-- local admin에 이미지 기능 권한을 명시적으로 연결합니다.
INSERT INTO auth_role_permission (role_code, permission_code, created_at, updated_at, created_by, updated_by)
SELECT role.role_code, permission.permission_code, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM auth_role role
JOIN auth_permission permission ON permission.permission_code IN (
    'dataset:image:read', 'dataset:image:upload', 'dataset:image:update', 'dataset:image:delete')
WHERE role.role_code = 'admin'
    AND NOT EXISTS (SELECT 1 FROM auth_role_permission
        WHERE role_code = 'admin' AND permission_code = permission.permission_code);
