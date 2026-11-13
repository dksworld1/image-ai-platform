INSERT INTO auth_role_permission (role_code, permission_code, created_at, updated_at, created_by, updated_by)
SELECT role.role_code, permission.permission_code, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM auth_role role
JOIN auth_permission permission ON permission.permission_code IN (
    'dataset:file:read', 'dataset:file:upload', 'dataset:file:update', 'dataset:file:delete')
WHERE role.role_code = 'admin'
    AND NOT EXISTS (SELECT 1 FROM auth_role_permission existing
        WHERE existing.role_code = role.role_code AND existing.permission_code = permission.permission_code);
