INSERT INTO auth_permission (permission_code, display_name, description, created_at, updated_at, created_by, updated_by)
SELECT p.code, p.name, p.name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0' FROM (VALUES
    ('pipeline:artifact:create', '아티팩트 생성'),
    ('pipeline:artifact:read', '아티팩트 조회'),
    ('pipeline:artifact:update', '아티팩트 기록·확정')
) p(code, name) WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.permission_code = p.code);
