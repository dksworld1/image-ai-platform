# DB 마이그레이션 설정

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
