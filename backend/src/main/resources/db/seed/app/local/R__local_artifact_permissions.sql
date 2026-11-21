INSERT INTO auth_role_permission (role_code, permission_code, created_at, updated_at, created_by, updated_by)
SELECT r.role_code, p.permission_code, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM auth_role r CROSS JOIN auth_permission p
WHERE r.role_code = 'admin' AND p.permission_code LIKE 'pipeline:artifact:%'
  AND NOT EXISTS (SELECT 1 FROM auth_role_permission existing WHERE existing.role_code = r.role_code AND existing.permission_code = p.permission_code);
