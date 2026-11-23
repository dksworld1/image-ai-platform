INSERT INTO auth_role_permission(role_code,permission_code,created_at,updated_at,created_by,updated_by)
SELECT role_code,'dataset:version:restore',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'0','0' FROM auth_role WHERE role_code='admin'
AND NOT EXISTS (SELECT 1 FROM auth_role_permission WHERE role_code='admin' AND permission_code='dataset:version:restore');
