# 배포 및 서버 설정 운영

## 이미지 업로드 저장소 — 2026-11-12

app V18을 기존 마이그레이션 활성화·DDL/common 위치 설정으로 적용합니다. common 반복 R__04_image_permissions.sql은 dataset:image:upload를 등록하고 local의 R__local_image_permissions.sql은 기존 admin 역할에만 부여합니다. 실행 순서는 버전 마이그레이션 → 공통 반복 01~04 → local app → local image permissions → local workspace입니다. 운영 역할은 자동 부여하지 않습니다.

이미지 저장·용량과 multipart 설정은 application.yaml에 직접 작성하며 별도 YAML import를 사용하지 않습니다. DB 접속 YAML import만 기존 방식을 유지합니다. 프로파일별 차이는 application-{profile}.yaml에서 재정의합니다. 다음 값은 외부 YAML로 덮어쓸 수 있으며 환경 변수·시작 스크립트에서 중복 설정하지 않습니다.

```yaml
app:
  storage:
    images:
      directory: /srv/image-ai/images
      max-file-size: 10MB
spring:
  servlet:
    multipart:
      max-request-size: 11MB
```

기본 directory는 backend 실행 디렉터리 기준 `./data/images`입니다. 서비스 계정에 저장·조회·실패 정리 권한이 있어야 합니다. 디렉터리는 업로드 시 생성하며 실제 파일명은 UUID.형식확장자입니다. DB storage_path는 상대 경로이므로 루트를 바꿀 때 기존 파일도 새 루트로 함께 이동해야 합니다. DB와 이미지 디렉터리를 함께 백업해야 원본명·파일 관계를 복원할 수 있습니다.

파일당 최대 용량은 app.storage.images.max-file-size 한 값으로 서비스와 spring.servlet.multipart.max-file-size를 맞춥니다. 요청 전체 제한에는 multipart 부가 데이터가 포함되므로 파일당 용량을 늘릴 때 max-request-size도 여유 있게 조정해야 합니다. 허용 형식 4종·최대 4천만 화소는 현재 코드 정책이며 설정화 후보는 agent-suggestions에서 별도로 검토합니다.

업로드 DB 실패 시 rollback 콜백이 작성 파일을 제거합니다. 정리 실패는 서버 로그에서 확인하며 별도 재시도 배치는 없습니다. 프로세스 중단이나 데이터셋 삭제 후 남은 실제 파일의 배치 정리는 후속 작업입니다. 데이터셋 삭제 시 메타데이터는 FK CASCADE로 제거하며 파일 삭제는 하지 않습니다. DB·파일 시스템이 하나의 분산 트랜잭션으로 묶인 것은 아닙니다.

이번 작업은 격리된 메모리 DB·target 테스트 폴더에서 업로드·원본명 중복·권한·DB 실패 파일 정리와 local 권한 연결을 검증했습니다. 실제 사용자 DB 적용·서버 재시작·운영 저장소 접근은 하지 않았습니다. 프런트 업로드 구현은 사용자 요청으로 취소했으며 기존 화면으로 다시 빌드했습니다.

## 데이터셋 삭제 백엔드 배포 — 2026-11-11

DELETE /api/datasets/{datasetId}를 추가했습니다. 신규 DDL·API 입력 화면은 없습니다. 기존 dataset:item:delete 권한을 사용하며 local 공간 seed 체크섬 변경 시 admin 역할에 삭제 기능 권한도 명시적으로 연결합니다. 생성자·공간 관리자 모두 삭제 기능 권한과 해당 공간의 ACTIVE 멤버십을 충족해야 합니다. 운영의 일반 사용자·관리자 역할에 대한 자동 권한 부여는 하지 않습니다. 운영에서 사용할 역할에는 기존 RBAC 방식으로 해당 권한을 부여해야 합니다.

실제 DB 삭제·마이그레이션 적용·사용자 서버 재시작은 하지 않았습니다. 관련 삭제 테스트 2개와 local seed 테스트 1개만 메모리 DB에서 실행해 통과했습니다. UI·이미지·배치는 이번 배포 범위가 아닙니다. [삭제 API 계약](../api/api.md#데이터셋-삭제)을 따릅니다.

## local 미귀속 데이터의 공통 공간 배정 — 2026-11-11

local에서는 V16 후 일회성 V17 DML이 workspace_id NULL인 데이터셋만 첫 번째 공통 공간으로 배정합니다. 기존 공간이 지정된 데이터는 이동하지 않습니다. [V17 조건과 실행 순서](database-migration.md#local-미귀속-데이터-이관-v17--2026-11-11)를 따릅니다. 기존 local DB에 admin 계정 또는 대상 공통 공간이 준비되어 있어야 하며, 미귀속 데이터만 있는 상태에서 둘 다 없는 환경을 임의 사용자 소유로 추정하지 않습니다. 실제 DB 적용·서버 재시작은 이번 작업에서 수행하지 않았습니다.

## 공간별 독립 데이터셋 배포 — 2026-11-11

현재 생성은 다중 공간 공유 대신 공간마다 다른 dataset_id를 발급합니다. app DB 백업 후 app DDL·common 위치를 모두 포함해 V14→V15→V16을 적용하고 프런트·백엔드를 함께 배포합니다. 생성 요청 workspaceIds는 유지하지만 응답은 단일 객체에서 각 workspaceId의 독립 데이터셋 배열로 바뀝니다. 이전 클라이언트는 호환되지 않습니다. [SQL 이관](database-migration.md#공간별-독립-데이터셋-v14v15v16--2026-11-11)을 따르며 과거 스크립트 repair는 하지 않습니다.

관리 화면·상세·수정·삭제는 후속입니다. 현재 실제 DB 적용·서버 재시작은 하지 않았습니다. 아래 공유 생성 배포 설명은 이전 계약 이력입니다.

## 데이터셋 목록·다중 공간 생성 배포 — 2026-11-11

app DB 백업 후 기존 설정의 app DDL·common seed를 모두 포함하여 V11→V12→V13을 적용합니다. [이관 순서](database-migration.md#다중-공간-데이터셋-v11v12v13--2026-11-11)를 따르며 기존 스크립트 repair는 필요하지 않습니다. 새 생성 API는 workspaceIds를 필수로 요구하므로 새 프런트·백엔드를 함께 배포합니다. 이전 클라이언트의 공간 없는 생성은 400이며 과거 미귀속 데이터는 목록에서 제외합니다.

local 공간 seed가 재실행되어 admin 소유의 두 번째 공통 공간을 추가합니다. 이미 초기화된 local DB에서도 목록·생성이 가능하도록 해당 seed가 admin 역할에 dataset:item:read·dataset:item:create를 명시적으로 연결합니다. 기존 연결·감사 값은 보존하며 운영 역할에는 자동 부여하지 않습니다. 서비스는 역할 이름으로 우회하지 않고 이 기능 권한을 검사합니다.

프런트 `/datasets`, `/datasets/new` 직접 접근은 index.html fallback을 유지합니다. 생성은 다중 공간 선택, 목록은 가입 공간 통합 조회입니다. 가입·공간 관리·상세·수정·삭제 화면은 후속입니다. 이전 서버로 단순 복귀해 공간 없는 생성을 재개하지 않도록 합니다. 실제 local·운영 DB 적용·서버 재시작은 이번 작업에서 하지 않았습니다.

## local H2 웹 콘솔 — 2026-11-11

local 프로파일의 기존 `spring.h2.console.enabled: true`, `path: /h2-console` 설정을 사용합니다. Spring Boot 4의 콘솔 자동 구성을 제공하는 `spring-boot-h2console` 의존성을 추가했습니다. local 전용 보안 필터 체인은 콘솔 경로에만 SAMEORIGIN 프레임과 콘솔 폼 요청을 허용하며 일반 API의 기존 보안 체인은 유지합니다. 별도 콘솔 서버·포트는 만들지 않습니다. [Spring Boot 공식 H2 콘솔 안내](https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.h2-web-console)를 참고합니다.

백엔드를 새 코드로 재시작한 뒤 기본 주소 `http://localhost:8080/h2-console/`로 접속합니다. 포트를 변경한 환경은 `bin/local.settings.ps1`의 BackendPort를 사용합니다. JDBC URL은 콘솔 기본 예시 대신 접속할 데이터소스의 실제 값을 입력합니다. backend 디렉터리에서 실행한 기본 local app DB는 다음과 같습니다.

| 항목 | 기본 local 값 |
|---|---|
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:./data/image-ai-app;DB_CLOSE_ON_EXIT=FALSE` |
| User Name | `sa` |
| Password | 빈 값 |

외부 YAML로 연결 설정을 바꿨다면 해당 실제 설정을 사용합니다. 콘솔은 앱 로그인 admin 계정이 아닌 DB 접속 정보로 로그인합니다. 다른 데이터소스는 각각의 `datasource.<이름>.url`을 사용합니다. 콘솔 활성화 설정과 전용 보안 체인은 local에 한정되며 외부 접속 허용 설정은 추가하지 않았습니다.

검증은 메모리 DB·임의 테스트 포트에서 `mvn test "-Dtest=H2ConsoleTest"` 1개만 실행해 통과했습니다. 콘솔 HTTP 200·HTML·SAMEORIGIN과 일반 헬스 경로의 DENY 헤더를 확인했습니다. 실제 local DB 연결·사용자 서버 재시작은 하지 않았습니다.

## 워크스페이스 기반 스키마 배포 — 2026-11-11

기존 app Flyway 활성화·위치 설정으로 V10을 적용하면 workspace·workspace_member와 dataset의 nullable 공간 FK가 추가됩니다. 과거 SQL·YAML은 변경하지 않았고 repair는 필요하지 않습니다. local에서만 admin 초기 데이터 다음에 공통 워크스페이스와 admin의 소유자·ADMIN 멤버십을 등록합니다. 재실행은 기존 공간·멤버십·감사 정보를 보존합니다. [SQL 순서와 전환 제약](database-migration.md#워크스페이스-v10local-초기-공간--2026-11-11)을 따릅니다.

이 배포는 스키마 기반만 추가합니다. 관리 화면·가입 승인·서비스 접근 검사·기존 데이터 귀속·공간 필수값 전환은 후속 작업이며 기존 데이터셋 생성 API의 동작은 유지합니다. 실제 로컬·운영 DB에 적용하거나 서버를 재시작하지 않았습니다. 격리된 테스트 DB에서 관련 테스트 3개를 통과했습니다.

## 데이터셋 권한 카탈로그 배포 — 2026-11-10

새 공통 반복 seed `db/seed/app/common/R__03_dataset_permissions.sql`을 추가했습니다. 기존 app 마이그레이션 활성화·위치 설정에서 `classpath:db/seed/app/common`을 포함하면 다음 마이그레이션 실행 시 데이터셋 조회·생성·사용·수정·삭제 권한을 등록합니다. YAML 변경과 과거 이력 repair는 필요하지 않습니다. [실행 순서와 보존 정책](database-migration.md#데이터셋-권한-공통-seed--2026-11-10)을 따릅니다.

이번 등록으로 기존 역할에 권한이 자동 부여되거나 서비스 접근 정책이 변경되지는 않습니다. 단, 새 local DB의 최초 local seed는 기존 정책대로 현재 카탈로그 전체를 로컬 admin에 연결합니다. 이미 적용된 local seed는 공통 파일 추가만으로 재실행되지 않습니다. 실제 로컬·운영 DB 적용과 서버 재시작은 이번 작업에서 수행하지 않았습니다. Java 21의 격리된 테스트 DB에서 전체 62개 테스트를 통과했으며 적용·보존·재실행 검증 상세는 위 마이그레이션 문서에 기록했습니다.

## 사용자 식별 필드 전환 배포 — 2026-11-10

배포 전 app DB를 백업하고 기존 계정·역할·감사 기록을 확인합니다. 마이그레이션 위치는 app DDL과 app/common seed를 모두 포함해야 합니다. [V7→V8→V9 순서](database-migration.md#사용자-식별-체계-v7v8v9--2026-11-10)로 전체 이관한 뒤 새 백엔드와 프런트를 함께 배포합니다. V8은 common/identity의 버전 DML이므로 common을 누락하지 않습니다. V1~V6의 체크섬은 유지하며 이번 변경 때문에 repair를 실행하지 않습니다.

로그인 요청은 userId·password로 변경됐고 응답은 토큰과 userKey·userId·username을 포함합니다. 기존 프런트의 username 요청은 동작하지 않습니다. 이전 username 전용 탭 세션은 새 프런트가 제거해 재로그인하며, 기존 JWT subject가 보존된 내부 키와 일치하면 서버에서는 같은 계정을 계속 가리킵니다. 로그인 아이디·실명이 바뀌어도 기존 내부 키를 재발급하지 않습니다.

실명은 기존 데이터에서 추정하지 않아 NULL일 수 있습니다. local 신규 admin은 UUID 키·로그인 아이디 admin·예시 이름 로컬 관리자이며 기존 admin의 키는 seed 재적용 시 보존합니다. 시스템 예약값 '0'은 내부 user_key에 적용되며 로그인 가능한 시스템 계정은 없습니다. DB 접속 설정의 datasource.username은 변경 대상이 아닙니다.

실제 로컬·운영 DB 마이그레이션·서버 재시작은 이번 코드 작업에서 수행하지 않았습니다. 아래 기존 날짜의 username 기반 절차·설명은 당시 이력입니다. 상세 식별 규칙은 [ADR-031](../decisions/ADR-031-user-identity-fields.md)을 따릅니다.

## 데이터셋 생성 API 배포 — 2026-11-10

새 백엔드 배포 시 기존 app 마이그레이션 위치에서 V6__create_dataset.sql을 적용해야 합니다. 이전 버전은 변경하지 않았으므로 이번 V6 추가로 repair가 필요하지 않습니다. local/test는 기존 활성 설정, dev/prod는 기존 외부 YAML의 명시적 활성화 절차를 따릅니다. 생성 API를 호출하는 새 프런트와 대응 백엔드를 함께 배포합니다. 테이블과 시각 타입은 H2에서 검증했으며 실제 로컬·운영 DB 적용은 수행하지 않았습니다. 별도 조회·상세 경로는 제공하지 않습니다.

## 생성 화면 경로 — 2026-11-10

데이터셋 생성 입력 화면은 `/datasets/new`에서 제공합니다. 프런트는 일반 링크와 기존 탭 세션으로 페이지를 다시 열며 새 라우터를 도입하지 않았습니다. Vite preview에서 직접 접근·새로고침을 검증했습니다. 정적 호스팅으로 배포할 때는 프런트 화면 경로를 `index.html`로 연결하는 fallback이 필요하며 `/api` 요청의 기존 전달 경계는 유지해야 합니다. 운영 호스팅 설정 변경·검증은 아직 수행하지 않았습니다. 실제 데이터셋 저장은 이번 구현 범위가 아닙니다.

## 공통 생성·수정 컬럼 배포 — 2026-11-10

배포 전 DB 백업과 `auth_user`의 예약값 계정(`username='0'`) 존재 여부를 확인합니다. 존재하면 사용자와 역할 연결을 별도 정리한 뒤 적용합니다. 시스템 ID `'0'`용 로그인 계정은 생성하지 않습니다.

app은 기존 YAML의 마이그레이션 활성화 절차로 V5를 적용한 뒤 새 백엔드를 사용합니다. V1~V4 파일은 유지하므로 이번 변경에 따른 repair는 필요하지 않습니다. 기본 비활성화 상태인 dev/prod는 기존 외부 YAML 방식으로 명시적으로 활성화해야 합니다. V5는 기존 행에 적용 시각과 시스템 ID를 채우며 실제 과거 이력을 복원하지 않습니다. local에서는 이후 반복 seed가 실행되어 생성 정보는 보존하고 수정 정보만 갱신합니다.

catalog/archive는 자동 마이그레이션 대상이 아닙니다. 기존 `foundation_probe`를 사용한다면 각 DB용 `db/migration/catalog/V1__add_common_audit_columns.sql`, `db/migration/archive/V1__add_common_audit_columns.sql`을 사전 프로비저닝에서 한 번 적용한 뒤 새 DAO를 사용합니다. 신규 DB는 [정의서의 생성 DDL](../db-schema/tables/foundation/foundation-probe.md)을 적용합니다. 기존 테이블에 CREATE를 반복하거나 두 DB의 SQL을 app에 적용하지 않습니다.

시간 타입은 현재 H2에서 검증하며 다른 운영 DB를 도입하면 해당 DB의 DDL 호환성을 확인해야 합니다. 실제 로컬·운영 DB 적용은 수행하지 않았습니다. 상세 규칙과 순서는 [공통 스키마 규칙](../db-schema/schema-rules.md)과 [마이그레이션 설정](database-migration.md)을 따릅니다. 아래 이전 날짜의 UI 관련 배포 설명은 과거 이력입니다.

## 기존 UI 모듈 철거 적용 — 2026-11-09

[ADR-027](../decisions/ADR-027-remove-ui-module.md)에 따라 V3 적용 이력은 보존하고 V4에서 ui_screen_permission → ui_menu → ui_screen 순서로 삭제합니다. 이 변경은 화면·메뉴 데이터를 영구 삭제하므로 DB 백업 후 적용합니다. 기존 UI 서비스나 프런트 연동과 함께 운영하지 않습니다.

공통 반복 seed는 이력 호환을 위해 기존 파일명을 유지합니다. R__01_ui_permissions.sql은 ui:screen:manage·ui:menu:manage의 역할 연결과 권한 행을 삭제하고, R__02_ui_navigation.sql은 SELECT 1만 실행합니다. local seed는 관리자 계정·역할·현재 권한 연결만 준비합니다. V4 DDL 후 공통 01 → 공통 02 → local seed 순서이며 다른 권한과 역할은 삭제하지 않습니다. 이후 재기동에서도 UI 데이터가 재생성되지 않습니다.

이번 작업은 실제 로컬·운영 DB에 적용하지 않았습니다. 마이그레이션 활성 프로파일은 다음 기동 시 적용되며 비활성 환경은 기존 절차대로 명시적으로 활성화해야 합니다. 예전 V3 체크섬 불일치가 있는 DB는 기존 정합성 절차를 먼저 따릅니다. 자동 repair나 이력 삭제는 수행하지 않습니다. 아래 과거 UI 등록 설명과 검증 기록은 철거 전 이력입니다.

갱신일: 2026-11-09

## 목적과 적용 범위

백엔드 fat JAR 실행 시 호스트 서버의 외부 YAML을 로딩하는 방법과 비밀정보 관리 방침을 설명합니다. 현재 구현과 실행 조건을 기준으로 작성하며 미구현 변경은 적용된 것으로 설명하지 않습니다. 서버 설정·비밀정보 주입·실행 옵션·배포 절차가 변경되면 같은 변경에서 이 문서를 갱신합니다.

## 메뉴·화면 API 배포 시 DB 준비

SQL 작성 규칙은 DDL·DCL → `db/migration`, DML → `db/seed`입니다. app 위치 목록을 외부 YAML로 덮어쓰는 경우 `classpath:db/migration/app`과 `classpath:db/seed/app/common`을 모두 포함합니다. local은 추가로 `classpath:db/seed/app/local`을 사용합니다. dev/prod의 기본 비활성화 정책은 유지합니다.

이번 이관으로 기존 V3의 체크섬이 변경됩니다. 기존 적용 DB는 백업과 SQL 변경 검토 후 동일한 Flyway 데이터소스·위치로 `repair()` → `migrate()` 절차를 수행해야 합니다. 애플리케이션이 자동으로 이력을 보정하지 않습니다. 실제 DB repair는 실행하지 않았습니다. [체크섬 정리](database-migration.md#기존-v3-적용-db의-체크섬-정리)를 참고하세요.

local 프로파일은 `db/seed/app/local`의 반복 SQL로 `admin` 계정·`admin` 역할, 전체 기존 권한 연결을 준비합니다. 시작·화면 관리·메뉴 관리 메타데이터는 common의 R__02_ui_navigation.sql에서 준비합니다. 비밀번호 `admin`은 로컬 개발 전용이며 seed에는 BCrypt 해시만 저장합니다. test·dev·prod의 기본 마이그레이션 위치에는 이 seed가 없습니다. 외부 설정으로 로컬 seed 경로를 운영에 추가하지 않습니다. 재적용 시 비밀번호와 seed 소유 화면·메뉴 설정을 초기값으로 맞추므로 상세 동작은 [로컬 seed 정의](database-migration.md#로컬-app-초기-데이터-seedapplocal)를 따릅니다. 실제 관리 React 화면은 아직 구현하지 않았습니다.

app의 `V3__create_ui_navigation.sql`을 기존 [DB별 마이그레이션 절차](database-migration.md)에 따라 적용해야 합니다. local/test는 기존 YAML에서 활성화되어 있고 dev/prod는 외부 YAML의 app.migration 설정을 명시적으로 관리합니다. 감사 DB에는 새 SQL을 추가하지 않습니다.

V3는 메뉴·화면 테이블 3개를 생성하고, 공통 db/seed/app/common/R__01_ui_permissions.sql이 두 관리 권한을 등록합니다. 공통 R__02_ui_navigation.sql은 시작·관리 화면과 메뉴를 등록합니다. 관리자 계정·역할 연결은 local seed에서만 생성합니다. 승인된 역할에 `ui:screen:manage`, `ui:menu:manage`를 연결한 뒤 관리 API로 데이터를 준비합니다. 일반 요청에서 테이블·권한을 자동 등록하거나 기동 전수 검사를 수행하지 않습니다. 스키마가 없으면 API의 DB 접근은 서버 오류로 실패하며 자동 baseline을 하지 않습니다. H2·MockMvc 전체 58개 테스트를 통과했으며 운영 DB 검증을 수행한 것은 아닙니다.

## 호스트 서버의 비밀정보 관리

- DB 사용자명·비밀번호와 JWT 서명 키 등 환경별 비밀정보는 호스트 서버의 외부 YAML에 저장합니다. 프로젝트 설정에 환경 변수를 사용하지 않습니다.
- 파일은 UTF-8로 저장하고 JDBC·인증 등 주제별로 나눕니다. 실제 비밀값을 저장소, JAR, 컨테이너 이미지 또는 배포 아카이브에 포함하지 않습니다.
- 예시 경로는 Windows의 `C:/secure/`, Linux의 `/etc/image-ai/`입니다. 서버에서 관리할 절대 경로를 정하고 앱 실행 계정에 읽기 권한, 운영 관리 주체에 필요한 수정 권한만 부여합니다. 상위 디렉터리 접근 권한도 함께 제한합니다.
- 실행 명령에는 파일 경로만 지정하고 비밀값을 직접 넣지 않습니다. 파일 내용을 로그·실행 결과·문서에 복사하지 않으며, 백업 파일도 같은 접근 정책으로 관리합니다.
- 비밀값 변경 후에는 앱을 재시작합니다. 현재 실행 중 자동 재로딩 기능은 없습니다.

예시 파일 구성:

```text
C:/secure/
  jdbc.yaml
  auth.yaml
```

`jdbc.yaml`의 구조 예시입니다. 아래 값은 실제 접속 정보로 바꿔야 하는 자리표시자입니다. 드라이버에 필요한 의존성이 배포 JAR에 포함되어 있어야 합니다.

```yaml
datasource:
  app:
    url: "<업무 DB JDBC URL>"
    username: "<업무 DB 사용자명>"
    password: "<업무 DB 비밀번호>"
    driver-class-name: "<JDBC 드라이버 클래스>"
  catalog:
    url: "<catalog DB JDBC URL>"
    username: "<catalog DB 사용자명>"
    password: "<catalog DB 비밀번호>"
    driver-class-name: "<JDBC 드라이버 클래스>"
  archive:
    url: "<archive DB JDBC URL>"
    username: "<archive DB 사용자명>"
    password: "<archive DB 비밀번호>"
    driver-class-name: "<JDBC 드라이버 클래스>"
```

현재 일반 실행은 업무 `app` DB 외에 legacy `catalog`·`archive` 데이터소스도 구성하므로 dev/prod 외부 파일에 필요한 연결 설정을 제공합니다.

`auth.yaml`의 구조 예시입니다. 운영용 키는 UTF-8 기준 32바이트 이상의 별도 비밀 키로 준비하고 아래 자리표시자를 교체합니다.

```yaml
app:
  auth:
    jwt:
      secret: "<운영 전용 JWT 서명 키>"
```

## additional-location으로 외부 파일 추가

`spring.config.additional-location`은 기본 설정 위치를 유지하면서 외부 파일을 추가합니다. 외부 파일에 동일한 키가 있으면 내부 YAML 값을 덮어쓰고, 외부에 없는 항목은 내부 설정을 유지합니다. 환경 변수·JVM 시스템 프로퍼티·명령행에서 같은 설정 키를 별도 지정하면 해당 소스가 YAML보다 우선할 수 있으므로 비밀값은 외부 YAML에 모아 관리합니다.

단일 파일 예시입니다. `local`은 설명용이며 운영 배포는 아래 dev/prod 절차를 따릅니다.

```powershell
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --spring.config.additional-location=file:C:/secure/jdbc.yaml
```

이 경우 내부 `application-local.yaml`이 import하는 `jdbc-local.yaml`의 값 위에 외부 JDBC 설정을 적용합니다. 다른 프로파일의 로컬 기본값을 운영값으로 간주하지 않습니다.

### 파일이 여러 개인 경우

쉼표로 파일 경로를 나열합니다. 동일 키가 여러 추가 파일에 있으면 뒤에 지정한 파일이 우선합니다. 주제별 파일에서는 가능한 한 키가 겹치지 않게 관리합니다.

```powershell
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local "--spring.config.additional-location=file:C:/secure/jdbc.yaml,file:C:/secure/auth.yaml"
```

공백이 포함된 경로는 위 예시처럼 옵션 전체를 따옴표로 감쌉니다. 각 파일 경로에 `file:`을 붙이고 파일명을 명시합니다. 파일 목록에서 쉼표 뒤에 불필요한 공백을 넣지 않습니다.

디렉터리를 지정할 수도 있지만, 이때는 마지막 `/`가 필요하고 기본적으로 `application.yaml`과 `application-{profile}.yaml` 같은 이름을 탐색합니다. 해당 디렉터리의 임의 이름 YAML을 전부 읽는 기능이 아니므로 `jdbc.yaml`, `auth.yaml`처럼 관리하는 경우 파일을 각각 지정합니다. 프로파일별 변형 파일이 있으면 그 파일도 로딩 대상이므로 함께 관리합니다.

필수 서버 설정에는 `optional:`을 붙이지 않습니다. 지정 파일이 없으면 시작 실패로 드러나게 합니다. `spring.config.location`은 기본 탐색 위치를 대체하므로 기존 설정을 유지하며 값을 추가하는 용도로 사용하지 않습니다.

### dev/prod 실행

서버의 JDBC·인증 YAML 경로를 지정합니다.

```powershell
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod "--spring.config.additional-location=file:C:/secure/jdbc.yaml,file:C:/secure/auth.yaml"
```

Linux 예시:

```bash
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod --spring.config.additional-location=file:/etc/image-ai/jdbc.yaml,file:/etc/image-ai/auth.yaml
```


## 정적 런타임 설정과의 경계

### 데이터소스별 DB 설정

프로파일 활성화, 다중 SQL 위치, seed, 빈 목록 처리는 [DB 마이그레이션 설정](database-migration.md)을 따릅니다.

local/test의 `audit` 데이터소스는 업무 DB와 별도의 H2 파일을 사용합니다. backend 기준 local 경로는 `./data/image-ai-audit`, test 경로는 `./target/image-ai-platform-audit-test`입니다. dev/prod에서 사용할 경우 외부 JDBC YAML에 `datasource.audit.url`, 사용자명, 비밀번호와 드라이버를 지정합니다.

Flyway 실행은 `app.migration.enabled`로 제어하며 기본값은 false, local/test에서는 true입니다. `app-locations`와 `audit-locations`은 같은 설정 그룹에서 각 DB의 SQL 경로를 지정합니다. 각 DB가 자기 이력 테이블을 관리합니다. 현재 audit의 실제 업무 SQL은 없습니다.

로컬에서는 Vite 개발 서버와 미리보기 서버가 시작 시 `bin/local.settings.ps1`의 `BackendPort`를 읽습니다. 포트를 바꾸면 백엔드와 Vite를 재시작합니다. 브라우저는 항상 같은 출처의 `/api`를 호출하므로 정적 파일 배포 시에는 Nginx의 `/api/` 프록시 대상에 배포 환경의 백엔드 주소를 지정합니다.

`RuntimeConfig`는 클래스패스 `config/runtime/*.properties`를 자체 로딩합니다. Spring의 `additional-location`은 이 정적 로더의 탐색 경로나 값을 변경하지 않습니다. DB 자격 증명은 Spring 데이터소스 생성에 사용하므로 외부 YAML에서 관리합니다. 런타임 로더의 폴더·파일 INFO/WARN 로그는 Spring이 읽은 외부 YAML의 로딩 목록을 의미하지 않습니다.

## 관련 문서와 유지 관리

### Windows 로컬 백엔드 JDK 선택

`bin/start-local-backend.ps1`은 `$PSScriptRoot`의 상위 저장소에서 `backend` 절대 경로를 구합니다. `Push-Location`으로 Maven 실행 위치를 고정하고 `-Dspring-boot.run.workingDirectory=<backend 절대 경로>`로 Spring Boot 자식 프로세스의 작업 디렉터리도 명시합니다. 호출 위치와 무관하게 local YAML의 `jdbc:h2:file:./data/...`는 `backend/data/`를 사용합니다. 종료 시 호출자의 작업 디렉터리는 복원합니다. IDE나 JAR 직접 실행에는 이 스크립트의 고정 정책이 적용되지 않습니다.

2026-11-09 검증: Spring Boot Maven Plugin 4.1.2의 실제 플러그인 명세에서 `spring-boot.run.workingDirectory` 속성을 확인했습니다. Windows PowerShell 5.1에서 저장소 루트·frontend·임시 폴더를 호출 위치로 자동 검증했습니다. JVM 호출만 대역으로 교체해 실제 스크립트의 Maven 실행 위치, `spring-boot:run` 목표, backend 작업 디렉터리 인자와 호출 위치 복원을 모두 확인했습니다. 이번 검증에서는 서버를 기동하거나 DB 파일을 이동·수정하지 않았습니다.

`bin/start-local-backend.ps1`은 `bin/local.settings.ps1`의 `BackendJavaHome`에 지정한 `bin/java.exe`로 Maven과 Spring Boot를 실행합니다. 기본값은 `C:\Program Files\Java\jdk-21`입니다. 클래스 파일 버전 65(Java 21)를 Java 17(최대 61)로 실행하는 오류를 해결하기 위해 런타임 선택을 명시했습니다. 이 경로는 Windows 로컬 실행 전용이며 서버 외부 YAML 설정과 별개입니다.

Maven은 PATH에서 찾은 `mvn`의 설치 디렉터리를 기준으로 표준 배포판의 `boot/plexus-classworlds-*.jar`와 `bin/m2.conf`를 사용해 직접 기동합니다. 실행 스크립트는 환경 변수를 읽거나 설정하지 않습니다. `mvn.cmd`의 환경 변수 옵션, rc 스크립트 및 `.mvn/jvm.config` 로딩은 이 실행 경로에 적용하지 않습니다. Maven 래퍼나 별도 shim 설치는 현재 지원 범위에 없습니다. Linux/macOS의 `.sh` 실행은 기존처럼 Java 21 이상을 사용하는 Maven 환경을 준비해야 합니다.

재검증 시 저장소 루트에서 다음 명령을 실행하고, 시작 로그의 Java 버전이 21 이상인지 확인합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\bin\start-local-backend.ps1
```

별도 PowerShell에서 응답을 자동 확인합니다.

```powershell
. .\bin\local.settings.ps1
$response = Invoke-RestMethod -Uri "http://localhost:$BackendPort/api/health"
if ($response.status -ne 'UP') { throw '백엔드 헬스체크 실패' }
```

2026-11-08 검증: Windows PowerShell 실행에서 Java 21.0.13으로 Spring Boot 기동, Tomcat 8080 포트 시작 및 헬스체크 `UP`을 확인했습니다. Maven 캐시 쓰기와 로컬 HTTP 확인에는 실행 환경의 추가 권한이 필요했습니다. 업무 코드 변경은 없으며 Maven 테스트는 이번 수정에서 실행하지 않았습니다.

### 로컬 프런트 실행과 검증

`bin/start-local.ps1`은 `bin/start-local-frontend.ps1`을 Windows PowerShell에서 실행합니다. 프런트 스크립트는 `SkipFrontendBuild`가 false이면 빌드 성공 후 `npm run preview`를 실행합니다. 미리보기 포트는 Vite가 `bin/local.settings.ps1`의 `FrontendPort`를 직접 읽으며 명령행으로 전달하지 않습니다. API 프록시 포트는 같은 파일의 `BackendPort`를 사용합니다.

Windows PowerShell에서 `npm run preview -- --port $Port`를 호출했을 때 실제 npm 스크립트가 `vite preview 4173`으로 실행되는 문제를 재현했습니다. 이때 Vite는 포트 숫자를 프로젝트 경로로 해석하므로 빌드가 성공했어도 해당 하위 경로의 `dist`가 없다는 오류가 발생했습니다. 포트를 설정 파일에서 읽도록 변경해 인자 전달을 제거했습니다.

재검증은 저장소 루트에서 다음 명령으로 통합 실행이 호출하는 프런트 스크립트를 직접 실행합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\bin\start-local-frontend.ps1
```

빌드가 성공하고 `vite preview` 뒤에 포트 숫자가 위치 인자로 출력되지 않으며 설정한 주소로 기동하는지 확인합니다. 별도 PowerShell에서 다음 자동 확인을 실행합니다.

```powershell
$settingsPath = Join-Path (Get-Location) 'bin/local.settings.ps1'
. $settingsPath
$response = Invoke-WebRequest -Uri "http://127.0.0.1:$FrontendPort/" -UseBasicParsing
if ($response.StatusCode -ne 200 -or $response.Content -notmatch 'id="root"') {
    throw '프런트 미리보기 응답 검증 실패'
}
```

2026-11-08 검증: 수정 전 Windows PowerShell에서 빌드 성공 후 `vite preview 4173`과 `dist` 오류를 재현했습니다. 수정 후 동일 프런트 스크립트에서 빌드 성공 및 `http://127.0.0.1:4173/` 기동 출력을 확인했습니다. HTTP 응답 검증은 실행 환경의 소켓 접근 제한·연결 시간 초과로 완료하지 못했습니다. 백엔드 실행과 두 창을 여는 통합 실행 자체는 이번 검증에서 실행하지 않았습니다.

- [아키텍처](../architecture/architecture.md)
- [애플리케이션 설정 모듈](../plans/backend/stage-application-settings.md)
- [ADR-004 외부 JDBC YAML](../decisions/ADR-004-datasource-config-files.md)
- [ADR-013 Spring YAML 우선 관리](../decisions/ADR-013-topic-based-spring-yaml.md)
- [Spring Boot 외부 설정 공식 문서](https://docs.spring.io/spring-boot/reference/features/external-config.html): 추가 위치, 우선순위, 파일 import, 디렉터리와 프로파일별 파일 처리의 기준

향후 외부 설정 경로·파일 구성·비밀정보 전달·프로파일·JAR 실행 옵션·배포 절차를 변경하면 이 문서의 명령, 예시 YAML, 현재 구현 제약과 검증 여부를 같은 변경에서 갱신합니다. 코드에서 제거한 호환 옵션은 문서에서도 제거하고 실행하지 않은 절차를 검증 완료로 기록하지 않습니다.

## 로컬 V3 체크섬 정리 완료 기록

2026-11-08 로컬 app 파일 DB를 백업한 뒤 V3 체크섬 불일치만 확인해 Flyway repair·migrate를 실행했습니다. 반복 seed 3개와 전체 이력 검증이 성공했고 관리자 역할·권한 연결 및 화면 정보를 확인했습니다. 이는 위 미실행 기록 이후의 로컬 조치이며 다른 환경 DB의 repair를 의미하지 않습니다. 백업 위치와 상세 결과는 [마이그레이션 검증 기록](database-migration.md#로컬-db-체크섬-정리-실행-결과)을 참고합니다. 백엔드는 재시작하지 않았습니다.

## 로컬 루프백 주소 로그인 CORS

local YAML의 app.cors.allowed-origin은 localhost와 127.0.0.1 각각의 5173·4173 출처를 허용합니다. 브라우저 주소가 127.0.0.1이면 Origin도 해당 호스트로 전달되며, Vite가 백엔드로 프록시한 요청에도 이 출처 검사가 적용됩니다. localhost 허용만으로 127.0.0.1이 허용되지는 않습니다. dev/prod 허용 출처는 이번 변경에서 확대하지 않았습니다.

2026-11-09 수정 전 실제 미리보기 프록시에 Origin=http://127.0.0.1:4173을 포함한 admin 로그인이 HTTP 403과 Invalid CORS request로 거부되는 것을 재현했습니다. Origin 없는 동일 요청은 HTTP 200이었습니다. 설정 변경 후 실행 중인 백엔드를 재시작해야 적용됩니다.

수정 후 Java 21.0.13·Maven 3.10.0에서 전체 60개 테스트가 통과했습니다. local 프로파일로 네 출처의 preflight와 admin 로그인 성공·토큰 발급을 MockMvc에서 확인하고 미허용 출처 거부 검증을 유지했습니다. 실행 중인 서버 DB를 사용하지 않도록 mvn test에 검증 전용 datasource.app.url=jdbc:h2:mem:loopback-login-app;DB_CLOSE_DELAY=-1 및 datasource.audit.url=jdbc:h2:mem:loopback-login-audit;DB_CLOSE_DELAY=-1을 지정했습니다. 실제 서버 재시작과 재시작 후 브라우저 조작은 수행하지 않았습니다.
