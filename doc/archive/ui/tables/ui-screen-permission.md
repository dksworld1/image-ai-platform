# `ui_screen_permission` 테이블 정의

- 그룹: 화면·메뉴
- 데이터소스: `app`
- 상태: 구현됨 — app V3 migration·DAO·서비스·API
- 생성 migration: `backend/src/main/resources/db/migration/app/V3__create_ui_navigation.sql`
- 사용 코드: `com.imageaiplatform.ui.dao.impl.MyBatisUiDao`, `com.imageaiplatform.ui.service.UiService`

## 목적

세부 권한이 필요한 화면을 열 수 있는 대표 권한을 연결합니다. 화면의 모든 버튼·API 권한을 나열하는 테이블이 아닙니다. 연결된 권한 중 하나 이상 보유하면 접근 가능합니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `screen_code` | `VARCHAR(100)` | 아니요 | 없음 | 화면 코드 |
| `permission_code` | `VARCHAR(150)` | 아니요 | 없음 | 기존 auth_permission의 권한 코드 |

## 제약 및 인덱스

- `pk_ui_screen_permission`: 기본 키 (screen_code, permission_code). 중복 연결을 막고 화면별 권한 조건을 조회합니다.
- `fk_ui_screen_permission_screen`: screen_code → ui_screen(screen_code).
- `fk_ui_screen_permission_permission`: permission_code → auth_permission(permission_code).
- 두 FK 모두 삭제·키 변경 NO ACTION입니다. 권한·화면을 삭제하기 전에 연결을 명시적으로 해제합니다.
- `idx_ui_screen_permission_permission`: (permission_code, screen_code). 권한에 연결된 화면을 찾는 조회를 지원합니다.

## 판정 규칙

- ui_screen.permission_required=TRUE인 비공개 화면에만 연결합니다. 다른 화면에 연결하지 않는 규칙은 쓰기 서비스에서 처리합니다.
- 여러 행은 ANY/OR입니다. ALL·역할명·와일드카드·임의 표현식은 저장하지 않습니다.
- 연결이 0개인 권한 필요 화면은 접근 거부입니다. 연결 누락을 인증 전용 화면으로 해석하지 않습니다.
- 사용자의 유효 권한은 기존 auth_user_role·auth_role_permission으로 구합니다. 사용자·역할별 화면 연결 테이블은 추가하지 않습니다.
- 이 화면 접근 조건을 통과해도 개별 API 작업 권한을 획득하는 것은 아닙니다.
