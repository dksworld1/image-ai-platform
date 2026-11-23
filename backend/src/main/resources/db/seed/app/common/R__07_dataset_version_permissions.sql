INSERT INTO auth_permission(permission_code, display_name, description, created_at, updated_at, created_by, updated_by)
SELECT p.code, p.name, p.name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0' FROM (VALUES
    ('dataset:version:create', '데이터셋 버전 생성'),
    ('dataset:version:read', '데이터셋 버전 조회')
) p(code, name) WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.permission_code=p.code);
