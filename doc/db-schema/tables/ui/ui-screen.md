# `ui_screen` 테이블 정의

- 그룹: 화면·메뉴
- 데이터소스: `app`
- 상태: 구현됨 — app V3 migration·DAO·서비스·API
- 생성 migration: `backend/src/main/resources/db/migration/app/V3__create_ui_navigation.sql`
- 사용 코드: `com.imageaiplatform.ui.dao.impl.MyBatisUiDao`, `com.imageaiplatform.ui.service.UiService`

## 목적

메뉴와 독립적인 화면 식별자와 접근 정책을 관리합니다. 여러 메뉴가 같은 화면을 참조할 수 있으며, 메뉴에 연결하지 않은 화면도 허용합니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `screen_code` | `VARCHAR(100)` | 아니요 | 없음 | 화면 코드, 기본 키 |
| `display_name` | `VARCHAR(100)` | 아니요 | 없음 | 화면 표시 이름 |
| `route_path` | `VARCHAR(255)` | 아니요 | 없음 | 프런트 내부 정적 경로 |
| `public_access` | `BOOLEAN` | 아니요 | `FALSE` | 공개 화면 여부 |
| `permission_required` | `BOOLEAN` | 아니요 | `TRUE` | 비공개 화면의 세부 권한 필요 여부 |
| `enabled` | `BOOLEAN` | 아니요 | `TRUE` | 화면 활성 여부 |

## 제약 및 인덱스

- `pk_ui_screen`: 기본 키 (`screen_code`).
- `uq_ui_screen_route`: 유일 제약 (`route_path`). 같은 정적 경로의 화면을 중복 정의하지 않습니다.
- `ck_ui_screen_access`: public_access=TRUE이면 permission_required=FALSE여야 합니다.
- screen_code 형식은 `^[a-z][a-z0-9-]*$`입니다. 경로는 `/`로 시작하는 정규화된 내부 경로로 제한하고 URL scheme·`//` 시작·쿼리·fragment를 허용하지 않습니다. 코드·이름·경로의 입력 검증은 쓰기 서비스에서 수행합니다.

## 접근 정책

| public_access | permission_required | 의미 |
|---|---|---|
| TRUE | FALSE | 공개 |
| FALSE | FALSE | 활성 계정 인증만 필요 |
| FALSE | TRUE | 인증 후 연결된 대표 권한 중 하나 필요 |
| TRUE | TRUE | 허용하지 않는 조합 |

- 기본값으로 생성한 비공개 화면은 권한 연결 전까지 접근할 수 없습니다.
- 권한 필요 화면의 연결이 비어 있으면 거부합니다. 공개·인증 전용으로 바꿀 때는 권한 연결을 같은 쓰기 트랜잭션에서 제거합니다. 이 행 간 규칙은 단일 행 CHECK만으로 보장하지 않습니다.
- enabled=FALSE이면 공개 여부와 무관하게 화면 접근 대상에서 제외합니다. 화면 비활성화가 연결된 API 자체를 중지시키는 것은 아닙니다.

## 관계 및 삭제 규칙

- ui_menu.screen_code와 ui_screen_permission.screen_code가 참조합니다.
- 메뉴 참조와 권한 연결이 남아 있으면 삭제를 거부합니다. 연결을 명시적으로 해제한 뒤 삭제합니다.
