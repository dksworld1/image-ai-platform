INSERT INTO auth_permission (permission_code, display_name, description)
SELECT 'ui:screen:manage', '화면 관리', '화면과 화면 접근 조건 관리'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'ui:screen:manage');

INSERT INTO auth_permission (permission_code, display_name, description)
SELECT 'ui:menu:manage', '메뉴 관리', '메뉴 계층과 노출 설정 관리'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'ui:menu:manage');
