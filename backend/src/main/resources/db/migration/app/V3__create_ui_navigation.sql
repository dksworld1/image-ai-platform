CREATE TABLE ui_screen (
    screen_code VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    route_path VARCHAR(255) NOT NULL,
    public_access BOOLEAN NOT NULL DEFAULT FALSE,
    permission_required BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_ui_screen PRIMARY KEY (screen_code),
    CONSTRAINT uq_ui_screen_route UNIQUE (route_path),
    CONSTRAINT ck_ui_screen_access CHECK (NOT public_access OR NOT permission_required)
);

CREATE TABLE ui_menu (
    menu_code VARCHAR(100) NOT NULL,
    parent_menu_code VARCHAR(100),
    node_type VARCHAR(10) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    screen_code VARCHAR(100),
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_ui_menu PRIMARY KEY (menu_code),
    CONSTRAINT fk_ui_menu_parent FOREIGN KEY (parent_menu_code) REFERENCES ui_menu(menu_code) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_ui_menu_screen FOREIGN KEY (screen_code) REFERENCES ui_screen(screen_code) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT ck_ui_menu_node CHECK (
        (node_type = 'GROUP' AND screen_code IS NULL) OR (node_type = 'SCREEN' AND screen_code IS NOT NULL)
    ),
    CONSTRAINT ck_ui_menu_parent CHECK (parent_menu_code IS NULL OR parent_menu_code <> menu_code),
    CONSTRAINT ck_ui_menu_sort CHECK (sort_order >= 0)
);
CREATE INDEX idx_ui_menu_parent_order ON ui_menu(parent_menu_code, sort_order, menu_code);
CREATE INDEX idx_ui_menu_screen ON ui_menu(screen_code, menu_code);

CREATE TABLE ui_screen_permission (
    screen_code VARCHAR(100) NOT NULL,
    permission_code VARCHAR(150) NOT NULL,
    CONSTRAINT pk_ui_screen_permission PRIMARY KEY (screen_code, permission_code),
    CONSTRAINT fk_ui_screen_permission_screen FOREIGN KEY (screen_code) REFERENCES ui_screen(screen_code) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_ui_screen_permission_permission FOREIGN KEY (permission_code) REFERENCES auth_permission(permission_code) ON DELETE NO ACTION ON UPDATE NO ACTION
);
CREATE INDEX idx_ui_screen_permission_permission ON ui_screen_permission(permission_code, screen_code);

INSERT INTO auth_permission (permission_code, display_name, description)
SELECT 'ui:screen:manage', '화면 관리', '화면과 화면 접근 조건 관리'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'ui:screen:manage');

INSERT INTO auth_permission (permission_code, display_name, description)
SELECT 'ui:menu:manage', '메뉴 관리', '메뉴 계층과 노출 설정 관리'
WHERE NOT EXISTS (SELECT 1 FROM auth_permission WHERE permission_code = 'ui:menu:manage');
