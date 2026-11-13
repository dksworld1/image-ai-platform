INSERT INTO auth_permission (permission_code, display_name, description, created_at, updated_at, created_by, updated_by)
SELECT 'dataset:image:upload', '데이터셋 이미지 업로드', '데이터셋에 원본 이미지 파일을 등록합니다.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'dataset:image:upload');

INSERT INTO auth_permission (permission_code, display_name, description, created_at, updated_at, created_by, updated_by)
SELECT permission.code, permission.name, permission.description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM (VALUES
    ('dataset:image:read', '데이터셋 이미지 조회', '이미지 목록과 원본·썸네일을 조회합니다.'),
    ('dataset:image:update', '데이터셋 이미지 수정', '기존 이미지를 새 버전으로 교체합니다.'),
    ('dataset:image:delete', '데이터셋 이미지 삭제', '개별 이미지 삭제 기능의 권한입니다. 삭제 API는 후속 구현입니다.')
) permission(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM auth_permission existing WHERE existing.permission_code = permission.code);
