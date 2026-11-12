-- 기존 local admin에도 새 업로드 기능 권한을 명시적으로 연결합니다.
INSERT INTO auth_role_permission (role_code, permission_code, created_at, updated_at, created_by, updated_by)
SELECT role.role_code, 'dataset:image:upload', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM auth_role role
WHERE role.role_code = 'admin'
    AND NOT EXISTS (SELECT 1 FROM auth_role_permission
        WHERE role_code = 'admin' AND permission_code = 'dataset:image:upload');
