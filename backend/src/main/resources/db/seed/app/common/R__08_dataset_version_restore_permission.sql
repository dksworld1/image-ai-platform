INSERT INTO auth_permission(permission_code,display_name,description,created_at,updated_at,created_by,updated_by)
SELECT 'dataset:version:restore','데이터셋 버전으로 편집 시작','확정 버전의 구성과 설명으로 현재 편집본을 교체합니다.',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'0','0'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code='dataset:version:restore');
