-- 데이터셋 기능 권한 카탈로그. 기존 정의·역할 연결·감사 정보는 보존합니다.
INSERT INTO auth_permission (permission_code, display_name, description, created_at, updated_at, created_by, updated_by)
SELECT permission.permission_code, permission.display_name, permission.description,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM (VALUES
    ('dataset:item:read', '데이터셋 조회', '데이터셋 목록과 상세 정보를 조회합니다.'),
    ('dataset:item:create', '데이터셋 생성', '데이터셋을 생성합니다.'),
    ('dataset:item:use', '데이터셋 사용', '데이터셋을 처리·분석 등 업무의 입력으로 사용합니다.'),
    ('dataset:item:update', '데이터셋 수정', '데이터셋 정보를 수정합니다.'),
    ('dataset:item:delete', '데이터셋 삭제', '데이터셋을 삭제합니다.')
) permission(permission_code, display_name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM auth_permission existing WHERE existing.permission_code = permission.permission_code
);
