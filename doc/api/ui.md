# 메뉴·화면 API

갱신일: 2026-11-08

## 엔드포인트

| 메서드 | 경로 | 접근 조건 | 응답 |
|---|---|---|---|
| GET | `/api/ui/navigation` | 공개, JWT가 있으면 해당 활성 사용자 기준 | 허용 화면·메뉴 목록 |
| GET | `/api/ui/screens/{code}` | 해당 화면의 접근 조건 | 화면 코드·이름·경로 |
| GET | `/api/ui/screens` | `ui:screen:manage` | 전체 화면 설정·권한 연결 |
| PUT | `/api/ui/screens/{code}` | `ui:screen:manage` | 생성 또는 전체 교체, 204 |
| DELETE | `/api/ui/screens/{code}` | `ui:screen:manage` | 삭제, 204 |
| GET | `/api/ui/menus` | `ui:menu:manage` | 전체 메뉴 설정 |
| PUT | `/api/ui/menus/{code}` | `ui:menu:manage` | 생성 또는 전체 교체, 204 |
| DELETE | `/api/ui/menus/{code}` | `ui:menu:manage` | 삭제, 204 |

관리 API에는 Bearer JWT가 필요합니다. 공통 seed는 관리 권한 정의와 시작·관리 화면·메뉴를 등록하며 관리자 계정·역할 연결은 생성하지 않습니다. local 프로파일은 별도 [로컬 seed](../operations/database-migration.md#로컬-app-초기-데이터-seedapplocal)로 admin 계정·역할·권한을 준비합니다. 공통 seed의 시작·관리 화면·메뉴는 프로파일과 무관한 기본 데이터이며 실제 노출은 사용자 접근 조건에 따라 달라집니다.

## 내비게이션 응답

```json
{
  "screens": [{"screenCode":"datasets","displayName":"데이터셋","routePath":"/datasets"}],
  "menus": [
    {"menuCode":"workspace","parentMenuCode":null,"nodeType":"GROUP","displayName":"작업 공간","screenCode":null,"sortOrder":0,"enabled":true},
    {"menuCode":"datasets-menu","parentMenuCode":"workspace","nodeType":"SCREEN","displayName":"데이터셋","screenCode":"datasets","sortOrder":0,"enabled":true}
  ]
}
```

- 메뉴는 parentMenuCode로 계층을 표현하는 평면 목록입니다. 프런트는 코드 Map으로 부모별 트리를 구성합니다. 형제는 sortOrder·menuCode 순이며 전체 배열에서 부모가 먼저 나온다고 가정하지 않습니다.
- 비활성 화면·메뉴·상위 메뉴의 하위 항목과 허용된 자식이 없는 그룹을 제외합니다. 메뉴가 없는 허용 화면도 screens에 포함합니다.
- 세부 권한 화면은 대표 권한 중 하나 이상 보유하면 허용합니다. 연결이 비어 있으면 거부합니다. 화면 진입·버튼·API 실행 권한은 별개입니다.
- 사용자명은 서버 인증 정보에서 얻습니다. username 쿼리로 다른 사용자를 지정할 수 없습니다. 삭제·비활성 계정이나 잘못된 JWT는 401입니다.
- 일반 응답에는 전체 역할·권한이나 화면별 권한 조건을 노출하지 않습니다.

## 화면 생성·전체 교체

`PUT /api/ui/screens/datasets`

```json
{
  "displayName":"데이터셋",
  "routePath":"/datasets",
  "publicAccess":false,
  "permissionRequired":true,
  "enabled":true,
  "permissionCodes":["dataset:item:read","dataset:item:write"]
}
```

모든 필드를 지정하며 Boolean null은 거부합니다. 권한은 auth_permission에 존재해야 합니다. 예시 데이터셋 권한은 자동 등록하지 않습니다. 공개는 publicAccess=true·permissionRequired=false, 인증 전용은 둘 다 false이고 permissionCodes는 빈 배열입니다. 권한 필요 화면의 빈 배열은 저장 가능하지만 접근은 거부합니다.

코드는 소문자 영문 시작의 영문·숫자·하이픈으로 최대 100자입니다. 경로는 `/`, `/admin/users` 같은 내부 정적 경로이며 외부 URL·쿼리·fragment·동적 경로·끝 슬래시는 지원하지 않습니다. 화면과 권한 연결 교체는 같은 app 트랜잭션입니다.

## 메뉴 생성·전체 교체

`PUT /api/ui/menus/datasets-menu`

```json
{"parentMenuCode":"workspace","nodeType":"SCREEN","displayName":"데이터셋","screenCode":"datasets","sortOrder":0,"enabled":true}
```

루트는 parentMenuCode=null, GROUP은 screenCode=null, SCREEN은 기존 화면 코드가 필요합니다. 부모는 GROUP이어야 하며 순환을 만들 수 없습니다. 메뉴 수정·삭제 시 기존 행을 코드 순으로 잠그고 처리합니다. H2 기본 READ_COMMITTED에서 동시 부모 변경을 검증했습니다. 자식 있는 그룹의 SCREEN 전환과 참조 중 삭제는 409이며, 연결 항목을 먼저 명시적으로 정리해야 합니다. 화면 삭제 실패 시 권한 연결 삭제도 롤백합니다.

## 오류·검증

| 상태 | 의미 |
|---|---|
| 400 | 필수값·정책·경로·부모 유형·순환 오류 |
| 401 | 인증 필요·토큰 오류·계정 없음 또는 비활성 |
| 403 | 화면 접근 또는 관리 권한 부족 |
| 404 | 화면 없음·비활성 화면, 삭제 대상 없음 |
| 409 | 경로 중복·없는 권한 연결·참조 중 삭제·자식 있는 그룹의 유형 전환 |
| 500 | DB 등 서버 장애 |

컨트롤러 오류는 `{"error":"FORBIDDEN"}` 등의 코드로 반환합니다. JSON 바인딩·Bean Validation·JWT 오류는 기존 Spring 처리도 사용하므로 모든 오류 본문이 같지는 않습니다.

메뉴·화면은 app DB를 사용하고 권한 조회는 auth 서비스 경계를 사용합니다. 관리 인가는 HTTP 인터셉터에서 수행하므로 다른 호출 경로를 추가할 때도 같은 인가 경계가 필요합니다. 사용자별 메뉴 테이블·캐시·기동 전수 검사·권한 자동 등록·프런트 편집 화면은 추가하지 않습니다.

검증: JDK 21·Maven 3.9.11에서 전체 58개 테스트 통과. 신규 UiApiTest 13개가 공개·인증·OR 권한, 관리 권한 분리, CRUD, 원자적 롤백, 비활성·회수 반영, 부모·순환 규칙과 동시 이동을 확인합니다. H2·MockMvc 검증이며 운영 DB·브라우저 검증은 아닙니다.

관련 스키마: [화면·메뉴 테이블 정의서](../db-schema/tables/ui/README.md).
