# `ui_menu` 테이블 정의

- 그룹: 화면·메뉴
- 데이터소스: `app`
- 상태: 구현됨 — app V3 migration·DAO·서비스·API
- 생성 migration: `backend/src/main/resources/db/migration/app/V3__create_ui_navigation.sql`
- 사용 코드: `com.imageaiplatform.ui.dao.impl.MyBatisUiDao`, `com.imageaiplatform.ui.service.UiService`

## 목적

탐색 메뉴의 계층·노출 이름·정렬 순서를 관리합니다. 메뉴의 접근 가능 여부는 연결된 화면 정책으로 판단하며 별도의 역할·권한 목록을 중복 저장하지 않습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `menu_code` | `VARCHAR(100)` | 아니요 | 없음 | 메뉴 코드, 기본 키 |
| `parent_menu_code` | `VARCHAR(100)` | 예 | 없음 | 부모 메뉴, NULL이면 루트 |
| `node_type` | `VARCHAR(10)` | 아니요 | 없음 | GROUP 또는 SCREEN |
| `display_name` | `VARCHAR(100)` | 아니요 | 없음 | 메뉴 표시 이름 |
| `screen_code` | `VARCHAR(100)` | 예 | 없음 | SCREEN 항목의 대상 화면 |
| `sort_order` | `INTEGER` | 아니요 | `0` | 같은 부모 아래 정렬 순서 |
| `enabled` | `BOOLEAN` | 아니요 | `TRUE` | 메뉴 노출 활성 여부 |

## 제약 및 인덱스

- `pk_ui_menu`: 기본 키 (`menu_code`).
- `fk_ui_menu_parent`: parent_menu_code → ui_menu(menu_code), 삭제·키 변경 NO ACTION.
- `fk_ui_menu_screen`: screen_code → ui_screen(screen_code), 삭제·키 변경 NO ACTION.
- `ck_ui_menu_node`: GROUP이면 screen_code가 NULL, SCREEN이면 NOT NULL입니다. 이 두 node_type만 허용합니다.
- `ck_ui_menu_parent`: 자기 자신을 부모로 지정하지 않습니다.
- `ck_ui_menu_sort`: sort_order >= 0.
- `idx_ui_menu_parent_order`: (parent_menu_code, sort_order, menu_code). 루트·형제 메뉴를 안정적으로 정렬합니다. sort_order 중복을 허용하고 동률은 menu_code로 정렬합니다.
- `idx_ui_menu_screen`: (screen_code, menu_code). 화면을 참조하는 메뉴 조회와 FK 확인을 지원합니다.

## 계층·노출 규칙

- 부모는 GROUP이어야 합니다. SCREEN은 하위 메뉴를 가지지 않습니다.
- 여러 노드로 구성된 순환은 쓰기 서비스에서 방지합니다. 자기 참조 CHECK와 FK만으로 전체 순환을 막았다고 간주하지 않습니다.
- GROUP은 표시 가능한 하위 화면 메뉴가 하나 이상 있어야 표시합니다. 그룹 자체를 클릭해 화면으로 이동하는 기능은 초기 범위에 없습니다.
- 비활성 노드의 하위 메뉴는 표시하지 않습니다. 하위 노드가 독립 루트로 승격되지는 않습니다.
- 같은 화면을 여러 메뉴에서 참조할 수 있으므로 screen_code에 유일 제약을 두지 않습니다.
- 메뉴별 경로는 중복 저장하지 않고 ui_screen.route_path를 사용합니다.
