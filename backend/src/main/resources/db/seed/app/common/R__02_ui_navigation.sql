-- app 공통 화면·메뉴 정보. 01_ui_permissions 이후 적용합니다.
MERGE INTO ui_screen (screen_code, display_name, route_path, public_access, permission_required, enabled)
KEY (screen_code) VALUES
('home', '시작 화면', '/', FALSE, FALSE, TRUE),
('screen-management', '화면 관리', '/admin/screens', FALSE, TRUE, TRUE),
('menu-management', '메뉴 관리', '/admin/menus', FALSE, TRUE, TRUE);

-- 이 seed가 소유하는 화면의 대표 권한만 명시적으로 맞춥니다.
DELETE FROM ui_screen_permission
WHERE screen_code IN ('home', 'screen-management', 'menu-management');
INSERT INTO ui_screen_permission (screen_code, permission_code) VALUES
('screen-management', 'ui:screen:manage'),
('menu-management', 'ui:menu:manage');

-- 부모 그룹을 먼저 넣고 자식 화면 메뉴를 연결합니다.
MERGE INTO ui_menu (menu_code, parent_menu_code, node_type, display_name, screen_code, sort_order, enabled)
KEY (menu_code) VALUES
('home-menu', NULL, 'SCREEN', '시작 화면', 'home', 0, TRUE),
('admin-menu', NULL, 'GROUP', '관리자', NULL, 100, TRUE);

MERGE INTO ui_menu (menu_code, parent_menu_code, node_type, display_name, screen_code, sort_order, enabled)
KEY (menu_code) VALUES
('screen-management-menu', 'admin-menu', 'SCREEN', '화면 관리', 'screen-management', 10, TRUE),
('menu-management-menu', 'admin-menu', 'SCREEN', '메뉴 관리', 'menu-management', 20, TRUE);
