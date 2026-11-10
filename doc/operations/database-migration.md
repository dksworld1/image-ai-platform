# DB 마이그레이션 설정

## 데이터셋 권한 공통 seed — 2026-11-10

`db/seed/app/common/R__03_dataset_permissions.sql`은 데이터셋 조회·생성·사용·수정·삭제의 다섯 권한을 `auth_permission`에 등록합니다. 전체 코드는 [권한 테이블 정의서](../db-schema/tables/auth/auth-permission.md#데이터셋-권한-카탈로그)를 따릅니다. DML이므로 공통 seed에 배치하며 기존 SQL과 YAML은 변경하지 않습니다. 현재 실행 순서는 app 버전 스크립트(V1~V9, common/identity의 V8 포함) → 공통 반복 01 → 02 → 03 → local 반복 seed(해당 프로파일에서 미적용 또는 체크섬 변경 시)입니다.

공통 03은 없는 권한만 삽입합니다. 기존 권한의 이름·설명·감사 정보와 역할 연결을 변경하지 않으며 별도 권한 부여도 하지 않습니다. 새 행의 생성·수정 시각은 동일한 타임스탬프, 생성자·수정자는 `'0'`입니다. 직접 재실행해도 중복·기존 정보 변경이 없습니다. 과거 스크립트의 체크섬 변경이나 repair는 필요하지 않습니다.

새 local DB에서는 이후 local seed가 모든 현재 권한을 admin 역할에 연결합니다. 기존 DB에서 이미 적용된 local seed는 이번 공통 파일 추가만으로 다시 실행되지 않으므로 새 권한을 자동 부여하지 않습니다. 역할 부여는 별도 승인 범위로 관리합니다. 실제 로컬·운영 DB 적용과 서버 재시작은 이번 작업에서 수행하지 않았습니다.

검증: Java·Maven 모두 Java 21.0.13 사용을 확인한 뒤 격리된 테스트 DB로 전체 `mvn test` 62개 통과, 실패·오류 0개. 신규 DB의 다섯 권한 등록, 기존 권한·역할 연결·감사 정보 보존, SQL 직접 재실행과 Flyway 재적용 시 중복 없음, 새 local admin의 초기 연결을 확인했습니다.

## 사용자 식별 체계 V7→V8→V9 — 2026-11-10

현재 사용자 식별은 user_key(내부 키)·user_id(로그인 아이디)·username(실명)입니다. [ADR-031](../decisions/ADR-031-user-identity-fields.md)에 따라 기존 키·역할 FK·감사 값을 보존합니다. 아래 이전 변경의 username 예약값 설명은 당시 구조이며 현재 예약 대상은 user_key='0'입니다.

| 순서 | 위치 | 내용 |
|---|---|---|
| V7 | db/migration/app/V7__separate_user_identity.sql | 기존 username과 역할 참조 컬럼을 user_key로 변경, user_id·실명 컬럼 추가 |
| V8 | db/seed/app/common/identity/V8__backfill_user_ids.sql | 기존 user_key를 초기 user_id로 복사. 실명·과거 감사 정보는 추정하거나 덮어쓰지 않음 |
| V9 | db/migration/app/V9__require_login_user_id.sql | 로그인 아이디 NOT NULL·UNIQUE 적용 |

app 위치 목록에는 `classpath:db/migration/app`과 `classpath:db/seed/app/common`을 함께 지정해야 합니다. common 하위 identity도 재귀 탐색하므로 V8이 V7과 V9 사이에 버전 순으로 실행됩니다. V8은 버전 DML이며 반복 R__ seed가 아닙니다. DDL 위치만 지정해 V8을 누락한 상태에서 V9를 먼저 적용하지 않습니다. 기존 행이 있으면 NOT NULL 적용에 실패할 수 있으며 빈 DB에서도 V8 이력이 누락됩니다. 기본 local/test 목록은 변경 없이 두 위치를 포함합니다. 외부 YAML로 목록 전체를 덮어쓰는 경우에도 반드시 함께 포함합니다.

V1~V6는 변경하지 않습니다. V9 이후 공통 반복 seed와 local seed가 실행됩니다. local seed는 user_id=admin 계정의 기존 user_key·생성 정보를 보존하고, 새 계정만 UUID 키와 예시 이름 로컬 관리자를 생성합니다. 시스템 작업자 값은 계속 내부 키 문자열 '0'입니다. 실제 DB에 자동 repair·데이터 삭제·키 재발급은 추가하지 않습니다.

검증: Java 21.0.13·Maven 3.10.0에서 전체 61개 테스트 통과. V6 기존 계정 여러 개의 로그인 아이디·비밀번호·역할·데이터셋 감사 값·시각 보존, 실명 NULL, V7→V8→V9 순서와 재적용 0건을 확인했습니다. 실제 로컬·운영 DB에는 적용하지 않았습니다.

최종 관련 5개 테스트 재실행도 통과했습니다. 실명 미상 로그인 응답이 username 필드를 null로 포함하는 것과 local seed가 최초 UUID 키를 재적용 후에도 유지하는 것을 추가 확인했습니다.

## 데이터셋 테이블 V6 — 2026-11-10

app V6에서 dataset 테이블을 생성합니다. 기존 V1~V5는 보존하며 추가 seed는 없습니다. 생성 요청은 모든 공통 컬럼을 명시하고 생성·수정 시각은 TIMESTAMP(6) WITH TIME ZONE에 동일한 UTC 시점으로 기록합니다. 정의는 [dataset 테이블](../db-schema/tables/dataset/dataset.md)을 따릅니다. 실제 로컬·운영 DB에는 적용하지 않았습니다.

## 공통 생성·수정 컬럼 적용 — 2026-11-10

[공통 스키마 생성 규칙](../db-schema/schema-rules.md)과 [ADR-028](../decisions/ADR-028-common-audit-columns.md)에 따라 app V5에서 인증·권한 5개 테이블에 `created_at`, `updated_at`, `created_by`, `updated_by`를 추가합니다. 기존 V1~V4는 변경하지 않으므로 이번 변경 때문에 해당 버전의 repair를 실행할 필요는 없습니다. 신규 DB와 기존 V4 DB 모두 동일한 최종 스키마를 구성합니다.

기존 행의 시각은 컬럼 추가 시각, 작업자는 시스템 예약값 문자열 `'0'`입니다. 과거 실제 작업자를 복원하는 값이 아닙니다. `auth_user.username='0'`은 CHECK 제약으로 금지합니다. 적용 전 DB를 백업하고 예약값 계정이 있으면 해당 계정과 역할 연결을 별도로 정리합니다. 자동 이름 변경이나 삭제는 수행하지 않습니다.

실행 순서는 app V1~V5 DDL → 공통 01 → 공통 02 → local seed입니다. local seed는 생성일·생성자를 보존하고 계정·역할 UPDATE 시 수정일과 수정자 `'0'`을 갱신합니다. 새 연결도 생성·수정 정보를 함께 기록합니다. local seed 체크섬 변경으로 local 환경에서 한 번 재실행됩니다.

legacy catalog/archive의 기존 `foundation_probe`에는 각각 `db/migration/catalog/V1__add_common_audit_columns.sql`, `db/migration/archive/V1__add_common_audit_columns.sql`을 한 번 수동 적용합니다. 이 DB들은 자동 마이그레이션 대상이 아니며 기본 SQL 위치나 서버 설정을 변경하지 않습니다. 신규 테이블 생성 DDL은 [정의서](../db-schema/tables/foundation/foundation-probe.md)를 따릅니다. audit 업무 테이블은 아직 없으며 검증용 fixture만 동일 규칙으로 변경했습니다.

실제 로컬·운영 DB에 대한 SQL 적용은 이번 작업에서 수행하지 않습니다. 아래 기존 날짜의 실행·검증 기록은 당시 이력입니다.

### 공통 컬럼 검증 기록

- 2026-11-10: Java·Maven 모두 Java 21.0.13 사용을 확인한 뒤 Maven 3.10.0에서 전체 `mvn test` 52개 통과, 실패·오류 0개.
- V4 데이터가 있는 DB의 V5 적용·기존 값 보존·공통 컬럼 보정·NOT NULL·예약 사용자 `'0'` 거부, MyBatis의 시간대 포함 시각과 작업자 매핑, seed 재실행 시 생성 정보 보존과 수정 정보 갱신, legacy DB별 ALTER와 probe INSERT를 검증했습니다.
- 마지막 인증·audit fixture 갱신 후 `mvn test "-Dtest=ApiAuthorizationHttpTest,AuthorizationDatabaseTest,DatabaseMigrationTest"` 18개를 재실행해 모두 통과했습니다.
- 두 실행 모두 app·audit·catalog·archive의 기본 URL을 테스트 전용 `jdbc:h2:mem:common-audit-*;DB_CLOSE_DELAY=-1`로 지정했습니다. 개별 테스트의 전용 DB 설정과 `target/dual-db-test-*` 격리 파일 DB도 사용했으며 실제 로컬·운영 DB에는 적용하지 않았습니다.

## 기존 UI 모듈 철거 적용 — 2026-11-09

[ADR-027](../decisions/ADR-027-remove-ui-module.md)에 따라 V3 적용 이력은 보존하고 V4에서 ui_screen_permission → ui_menu → ui_screen 순서로 삭제합니다. 이 변경은 화면·메뉴 데이터를 영구 삭제하므로 DB 백업 후 적용합니다. 기존 UI 서비스나 프런트 연동과 함께 운영하지 않습니다.

공통 반복 seed는 이력 호환을 위해 기존 파일명을 유지합니다. R__01_ui_permissions.sql은 ui:screen:manage·ui:menu:manage의 역할 연결과 권한 행을 삭제하고, R__02_ui_navigation.sql은 SELECT 1만 실행합니다. local seed는 관리자 계정·역할·현재 권한 연결만 준비합니다. V4 DDL 후 공통 01 → 공통 02 → local seed 순서이며 다른 권한과 역할은 삭제하지 않습니다. 이후 재기동에서도 UI 데이터가 재생성되지 않습니다.

이번 작업은 실제 로컬·운영 DB에 적용하지 않았습니다. 마이그레이션 활성 프로파일은 다음 기동 시 적용되며 비활성 환경은 기존 절차대로 명시적으로 활성화해야 합니다. 예전 V3 체크섬 불일치가 있는 DB는 기존 정합성 절차를 먼저 따릅니다. 자동 repair나 이력 삭제는 수행하지 않습니다. 아래 과거 UI 등록 설명과 검증 기록은 철거 전 이력입니다.

갱신일: 2026-11-08

## 현재 실행 방식

Spring이 시작할 때 `DatabaseMigrationConfiguration`에서 데이터소스별 Flyway 빈을 만듭니다. `appFlyway`는 `appDataSource`, `auditFlyway`는 `auditDataSource`에 명시적으로 연결하고 해당 위치 목록의 SQL을 적용합니다. 각 DB의 `flyway_schema_history`가 버전과 체크섬을 독립적으로 관리합니다. catalog/archive는 마이그레이션 대상에서 제외됩니다.

두 DB의 적용은 각 연결에서 실행되며 전체를 묶는 분산 트랜잭션은 없습니다. 한쪽에서 SQL 적용이 실패하면 앱 시작도 실패하며 다른 DB에 이미 반영된 변경은 남을 수 있습니다.

## 프로파일별 활성화

| 파일 | enabled | SQL 위치 |
|---|---|---|
| application.yaml | false | app·audit 스키마와 app 공통 seed 경로를 정의 |
| application-local.yaml | true | app 스키마 + 공통 seed + `db/seed/app/local`, audit는 공통 목록 상속 |
| application-test.yaml | true | 공통 목록 상속. 개별 테스트는 전용 위치로 덮어쓸 수 있음 |
| application-dev.yaml / application-prod.yaml | 공통 false 상속 | 외부 YAML에서 활성화·목록 지정 가능 |

공통 설정은 다음과 같습니다.

```yaml
app:
  migration:
    enabled: false
    app-locations:
      - classpath:db/migration/app
      - classpath:db/seed/app/common
    audit-locations:
      - classpath:db/migration/audit
```

로컬과 테스트는 각 프로파일 YAML에서 활성화합니다.

```yaml
app:
  migration:
    enabled: true
```

DB 연결 정보는 기존 `config/datasource/jdbc-local.yaml` 또는 `jdbc-test.yaml`에서 읽습니다. backend 디렉터리 기준 파일 위치는 다음과 같습니다.

| DB | local | test |
|---|---|---|
| app | ./data/image-ai-app | ./target/image-ai-platform-app-test |
| audit | ./data/image-ai-audit | ./target/image-ai-platform-audit-test |

audit 데이터소스와 Flyway 빈은 `datasource.audit.url`이 있을 때 생성됩니다.

## SQL 파일과 seed

작성 원칙: DDL·DCL은 `db/migration/`, DML은 `db/seed/`에서 관리합니다. `auth_permission`·역할 연결 테이블의 행 변경도 DML입니다. 공통 데이터는 `db/seed/app/common`, 로컬 전용 계정·역할 연결은 `db/seed/app/local`에 둡니다. 현재 app 공통 seed는 `R__01_ui_permissions.sql`, `R__02_ui_navigation.sql`이며 버전 DDL 이후, `R__local_app_seed.sql` 이전에 실행됩니다. 목록의 나열 순서가 실행 순서를 정하지 않습니다.

### 기존 V3 적용 DB의 체크섬 정리

이번 요청으로 V3에서 권한 INSERT 두 개를 seed로 옮겨 V3 체크섬이 바뀌었습니다. 기존 V3 적용 DB는 그대로 재시작하면 검증에 실패합니다. 해당 DB를 백업하고 변경이 두 INSERT의 이관인지 검토한 뒤, 동일한 데이터소스와 SQL 위치를 지정한 Flyway에서 `repair()` 후 `migrate()`를 실행합니다. 이 작업은 이번 변경에서 실제 DB에 실행하지 않았습니다. 자동 repair·이력 테이블 직접 UPDATE는 추가하지 않습니다. 상세 결정은 [ADR-023](../decisions/ADR-023-ddl-dml-script-separation.md)을 따릅니다.

`backend/src/main/resources/`가 클래스패스 루트입니다. 현재 업무 SQL은 아래 세 파일입니다.

```text
db/migration/app/V1__create_auth_user.sql
db/migration/app/V2__create_auth_rbac.sql
db/migration/app/V3__create_ui_navigation.sql
```

audit의 실제 업무 SQL은 아직 없습니다. 이후 `db/migration/audit/`에 해당 DB용 SQL을 추가합니다. 파일 번호는 DB별로 독립적입니다. 적용된 버전 파일은 유지하고 후속 변경은 새 버전 파일로 추가합니다.

V3는 app DB에 ui_screen·ui_menu·ui_screen_permission 테이블만 추가합니다. ui:screen:manage·ui:menu:manage 권한 정의는 db/seed/app/common/R__01_ui_permissions.sql에서 등록합니다. 기존 같은 코드의 권한 정의는 유지합니다. 공통 R__02_ui_navigation.sql은 시작·관리 화면과 메뉴를 등록하며 관리자 계정·역할 연결은 local seed에서만 생성합니다. 메뉴 API를 사용할 배포에서는 app V3를 먼저 적용하고 승인된 관리 역할에 필요한 권한을 연결해야 합니다. audit DB 스키마에는 영향을 주지 않습니다.

seed가 필요한 프로파일의 YAML에서 스키마·seed 경로를 함께 지정합니다. app 공통 seed는 실제 경로이며 audit seed는 예시입니다.

```yaml
app:
  migration:
    enabled: true
    app-locations:
      - classpath:db/migration/app
      - classpath:db/seed/app/common
    audit-locations:
      - classpath:db/migration/audit
      - classpath:db/seed/audit/common
```

프로파일의 목록은 공통 목록 전체를 대체하므로 프로파일 seed를 추가할 때 스키마와 공통 seed 경로도 함께 적습니다. 파일 실행 순서는 목록 순서가 아니라 Flyway의 버전·반복 스크립트 규칙으로 결정됩니다. `R__이름.sql`은 처음 적용되거나 내용의 체크섬이 변경되면 실행됩니다. 같은 데이터를 다시 넣어도 중복되지 않도록 작성합니다.

현재 공통 테스트 계정은 `@Sql`이 `fixtures/auth-user.sql`을 읽어 준비합니다. 다중 위치 검증 테스트는 별도의 `fixtures/audit-migration`과 `fixtures/audit-seed`를 사용합니다.

## 로컬 app 초기 데이터 (`seed.app.local`)

`backend/src/main/resources/db/seed/app/local/R__local_app_seed.sql`을 local 프로파일의 app 위치 목록에 연결했습니다. test·dev·prod의 기본 목록에는 추가하지 않습니다. app V1~V3와 공통 권한 seed 이후 반복 스크립트로 적용됩니다. 이번 DML 분리로 변경한 V3는 위 체크섬 정리 절차를 따릅니다.

```yaml
app:
  migration:
    enabled: true
    app-locations:
      - classpath:db/migration/app
      - classpath:db/seed/app/common
      - classpath:db/seed/app/local
```

공통·local 초기 데이터는 다음과 같습니다. 화면·메뉴는 common, 계정·역할·전체 권한 연결은 local SQL에 속합니다.

| 구분 | 내용 |
|---|---|
| 계정 | `admin`, 로컬 전용 비밀번호 `admin`의 BCrypt 해시, 활성 상태 |
| 역할 | 소문자 `admin`, 시스템 역할, admin 계정에 연결 |
| 권한 | 적용 시점 `auth_permission`의 모든 권한을 `admin` 역할에 명시 연결. 현재 API 권한은 `ui:screen:manage`, `ui:menu:manage` |
| 화면 | `home` → `/`(인증 전용), `screen-management` → `/admin/screens`, `menu-management` → `/admin/menus`(각 관리 권한 필요) |
| 메뉴 | 시작 화면, 관리자 그룹 아래 화면 관리·메뉴 관리. 형제 순서는 0, 그룹 100 및 자식 10·20 |

권한 이름 `admin`이나 와일드카드를 인가 우회로 사용하지 않습니다. 조회 시점의 모든 권한을 동적으로 허용하는 기능도 추가하지 않습니다. 미래 권한이 추가되면 seed SQL의 체크섬을 갱신하거나 새 데이터 마이그레이션으로 역할 연결을 보충해야 합니다. 내용이 그대로면 Flyway는 다음 기동에서 seed를 재실행하지 않습니다.

SQL은 H2 로컬 DB용 `MERGE`와 중복 없는 연결 삽입을 사용합니다. seed 재적용 시 위 계정의 비밀번호·활성 상태, 역할 정보, 세 화면·네 메뉴 및 해당 화면의 대표 권한 연결을 정의한 값으로 맞춥니다. 다른 계정·역할·메뉴를 삭제하지 않고 기존 `ADMIN` 같은 별도 역할·사용자 연결도 유지합니다. 경로가 다른 화면 코드에 이미 등록되어 있으면 고유 제약에 따라 실패하므로 기존 충돌 데이터를 명시적으로 정리해야 합니다.

현재 프런트는 로그인 후 시작 화면만 구현되어 있습니다. 화면·메뉴 관리 **API에 대응하는 화면 메타데이터**를 등록한 것이며 `/admin/screens`, `/admin/menus`의 React UI·동적 내비게이션 구현 완료를 뜻하지 않습니다. 인덱스 최초 로그인 정책도 유지합니다.

## 특정 DB 건너뛰기

두 위치 목록은 생략하거나 비워 둘 수 있습니다. 최종 바인딩된 목록이 비어 있으면 해당 DB에 migrate를 호출하지 않습니다. 공통 목록을 특정 프로파일에서 비우려면 아래처럼 명시합니다.

```yaml
app:
  migration:
    enabled: true
    audit-locations: []
```

공통 YAML에 정의된 목록은 프로파일에서 키를 생략하면 상속됩니다. 전체 실행을 끄려면 `app.migration.enabled: false`를 설정합니다.

## 외부 YAML로 설정

서버의 외부 YAML에 연결 정보와 필요한 마이그레이션 설정을 작성하고 기존 additional-location 방식으로 로딩합니다. 예를 들어 `C:/secure/migration.yaml`에 enabled와 위치 목록을 정의할 수 있습니다.

```powershell
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod "--spring.config.additional-location=file:C:/secure/jdbc.yaml,file:C:/secure/auth.yaml,file:C:/secure/migration.yaml"
```

활성화한 서버의 DB 계정에는 대상 SQL과 이력 테이블을 생성·변경할 권한이 필요합니다. 기존 테이블이 있는데 적용 이력이 없는 DB는 별도 처리 방식을 결정해야 합니다. 현재 구성은 자동 baseline을 지정하지 않습니다.

## 검증 기록

- Java 21.0.13·Maven 3.10.0에서 다중 위치 구현 후 전체 43개 테스트 통과.
- local 활성화·audit 위치 생략 처리 후 전체 44개 테스트 통과.
- app 위치 생략 처리 후 관련 테스트 3개 통과. 두 위치 생략 시 DB 연결을 사용하지 않고 빈 생성 성공을 확인했습니다.
- 파일 DB별 SQL 격리, 버전·seed 위치 동시 적용과 재실행 0건을 검증했습니다.
- 2026-11-08: app V3 메뉴·화면 스키마 추가 후 전체 58개 테스트 통과. DB별 스크립트 격리·재실행 0건과 메뉴·화면 API를 함께 확인했습니다.

## 관련 문서

- [배포 및 서버 설정 운영](deployment.md)
- [데이터소스별 Flyway 결정](../decisions/ADR-018-per-datasource-migrations.md)

## 로컬 seed 검증 기록

- 2026-11-08: JDK 21.0.13·Maven 3.10.0에서 신규 LocalAppSeedTest 포함 전체 62개 테스트 통과.
- 기존 V3 DB에 seed 경로 추가 적용, BCrypt admin 비밀번호, 소문자 admin 역할, 전체 권한 연결, 세 화면·네 메뉴 및 대표 권한 연결을 확인했습니다. Flyway 변경 없는 재실행은 0건이고 SQL 직접 재실행은 중복 없이 추가 권한도 연결했습니다.
- 실행 중인 로컬 서버의 audit DB 파일과 충돌하지 않도록 mvn test "-Ddatasource.audit.url=jdbc:h2:mem:local-seed-check-audit;DB_CLOSE_DELAY=-1"로 검증했습니다. 실제 로컬 파일 DB 적용과 브라우저 메뉴 검증은 실행하지 않았습니다. 다음 local 백엔드 기동 시 seed가 적용됩니다.

## DDL·DML 분리 검증 기록

- 2026-11-08: JDK 21.0.13·Maven 3.10.0에서 전체 62개 테스트 통과. DDL만 적용한 DB의 권한 행 0건, 공통·로컬 seed 순서 적용 2건, 기존 관리 권한 등록 및 admin 전체 권한 연결, 중복 없는 SQL 재실행과 Flyway 재실행 0건을 확인했습니다.
- 기존 V3 체크섬을 가진 파일 DB를 수정하지 않도록 mvn test "-Ddatasource.app.url=jdbc:h2:mem:dml-split-app;DB_CLOSE_DELAY=-1" "-Ddatasource.audit.url=jdbc:h2:mem:dml-split-audit;DB_CLOSE_DELAY=-1"로 새 검증 DB를 사용했습니다. 실제 DB repair는 수행하지 않았습니다.

## DB별·프로파일별 seed 분류

- app/common/R__01_ui_permissions.sql: app 공통 관리 권한 정의.
- app/common/R__02_ui_navigation.sql: app 공통 시작·관리 화면, 대표 권한 연결 및 메뉴 계층.
- app/local/R__local_app_seed.sql: 로컬 admin 계정·admin 역할·계정 역할 연결·전체 기존 권한 연결.
- 기본 위치는 common만 지정하고 local은 두 범위를 명시합니다. 상위 app 경로는 재귀 탐색으로 local 데이터까지 포함할 수 있으므로 위치로 지정하지 않습니다.
- SQL 파일명은 기존 두 파일을 유지하므로 단순 경로 이동은 이력에 새로운 스크립트를 만들지 않습니다. 이번에 화면·메뉴 SQL을 분리해 local seed 체크섬이 바뀌며, 신규 공통 seed 후 local seed가 실행됩니다. 기존 V3 체크섬 정리 필요성은 이전 변경과 같습니다.
- [ADR-024](../decisions/ADR-024-seed-database-scope.md)를 따릅니다. 테스트 fixture의 audit-seed는 격리된 검증 데이터이며 제품 seed 폴더 분류 대상이 아닙니다.

- 2026-11-08 재분류 검증: Java 21.0.13·Maven 3.10.0에서 전체 62개 통과. common만 적용한 DB의 화면 3개·메뉴 4개 및 계정·역할 0개를 확인했고 local 적용 후 admin 계정·전체 권한 연결과 SQL 재실행을 확인했습니다. mvn test에 검증 전용 datasource.app.url=jdbc:h2:mem:seed-folders-app;DB_CLOSE_DELAY=-1 및 datasource.audit.url=jdbc:h2:mem:seed-folders-audit;DB_CLOSE_DELAY=-1을 지정했습니다. 실제 로컬 DB 적용은 수행하지 않았습니다.

## 로컬 DB 체크섬 정리 실행 결과

- 2026-11-08: 로컬 backend/data/image-ai-app.mv.db를 backend/data/backups/image-ai-app-before-v3-repair-20261108-222110.mv.db로 백업했습니다. DB 파일이 사용 중이지 않은 상태에서 실행했습니다.
- 실제 불일치는 V3의 -279982466 → 1990233926뿐이며 추가로 공통·local 반복 seed 3개가 미적용 상태임을 확인했습니다. 동일 app DB와 현재 filesystem SQL 경로에 Flyway repair() 후 migrate()를 실행했습니다.
- 공통 권한·화면 메뉴·로컬 admin seed 3개 적용 및 전체 6개 마이그레이션 validate() 성공을 확인했습니다. admin→admin 역할, 모든 등록 권한 연결, 화면 3개의 존재를 조회 검증했습니다.
- 이 기록은 앞선 실제 로컬 DB 미적용·repair 미실행 기록 이후의 조치입니다. 다른 DB에는 repair를 실행하지 않았고 애플리케이션 기동 코드는 변경하지 않았습니다. 서버는 중지 상태로 두었습니다.

## UI 모듈 철거 검증 — 2026-11-09

- 프런트: `node --test frontend/test/*.test.cjs` 9개 통과, `npm --prefix frontend run build` 성공. 로그인·세션 복원·로그아웃·만료 처리는 유지하고 로그인 후 메뉴를 렌더링하지 않는 것을 확인했습니다.
- 백엔드: Java 21.0.13·Maven 3.10.0 확인 후 `mvn clean test`를 별도 메모리 DB에서 실행해 삭제 리소스를 정리했고, 최종 추가 검증을 포함한 `mvn test "-Ddatasource.app.url=jdbc:h2:mem:remove-ui-app;DB_CLOSE_DELAY=-1" "-Ddatasource.audit.url=jdbc:h2:mem:remove-ui-audit;DB_CLOSE_DELAY=-1"`에서 49개 통과, 실패·오류 0개.
- 신규 DB의 최종 UI 테이블 부재, 기존 V3 데이터의 V4 제거, UI 권한·역할 연결만 정리, 다른 권한 보존, 재적용 0건과 제거 API의 404 응답을 검증했습니다. 실제 로컬·운영 DB 마이그레이션이나 브라우저 검증은 실행하지 않았습니다.
