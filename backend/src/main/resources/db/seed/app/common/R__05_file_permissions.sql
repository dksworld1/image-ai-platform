INSERT INTO auth_permission (permission_code, display_name, description, created_at, updated_at, created_by, updated_by)
SELECT permission.code, permission.name, permission.description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM (VALUES
    ('dataset:file:read', '데이터 파일 조회', '데이터 파일 목록과 원본을 조회합니다.'),
    ('dataset:file:upload', '데이터 파일 업로드', '데이터셋에 이미지 외 데이터 파일을 등록합니다.'),
    ('dataset:file:update', '데이터 파일 수정', '데이터 파일 교체와 설명 수정을 수행합니다.'),
    ('dataset:file:delete', '데이터 파일 삭제', '데이터 파일을 삭제 표시하고 이력을 남깁니다.')
) permission(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.permission_code = permission.code);
