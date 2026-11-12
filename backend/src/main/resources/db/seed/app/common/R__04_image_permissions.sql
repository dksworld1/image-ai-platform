INSERT INTO auth_permission (permission_code, display_name, description, created_at, updated_at, created_by, updated_by)
SELECT 'dataset:image:upload', '데이터셋 이미지 업로드', '데이터셋에 원본 이미지 파일을 등록합니다.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'dataset:image:upload');
