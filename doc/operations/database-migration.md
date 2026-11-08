# DB 마이그레이션 설정

갱신일: 2026-11-07

## 현재 실행 방식

Spring이 시작할 때 `DatabaseMigrationConfiguration`에서 데이터소스별 Flyway 빈을 만듭니다. `appFlyway`는 `appDataSource`, `auditFlyway`는 `auditDataSource`에 명시적으로 연결하고 해당 위치 목록의 SQL을 적용합니다. 각 DB의 `flyway_schema_history`가 버전과 체크섬을 독립적으로 관리합니다. catalog/archive는 마이그레이션 대상에서 제외됩니다.

두 DB의 적용은 각 연결에서 실행되며 전체를 묶는 분산 트랜잭션은 없습니다. 한쪽에서 SQL 적용이 실패하면 앱 시작도 실패하며 다른 DB에 이미 반영된 변경은 남을 수 있습니다.

## 프로파일별 활성화

| 파일 | enabled | SQL 위치 |
|---|---|---|
| application.yaml | false | app·audit 스키마 경로를 정의 |
| application-local.yaml | true | 공통 목록 상속 |
| application-test.yaml | true | 공통 목록 상속. 개별 테스트는 전용 위치로 덮어쓸 수 있음 |
| application-dev.yaml / application-prod.yaml | 공통 false 상속 | 외부 YAML에서 활성화·목록 지정 가능 |

공통 설정은 다음과 같습니다.

```yaml
app:
  migration:
    enabled: false
    app-locations:
      - classpath:db/migration/app
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

`backend/src/main/resources/`가 클래스패스 루트입니다. 현재 업무 SQL은 아래 세 파일입니다.

```text
db/migration/app/V1__create_auth_user.sql
db/migration/app/V2__create_auth_rbac.sql
db/migration/app/V3__create_ui_navigation.sql
```

audit의 실제 업무 SQL은 아직 없습니다. 이후 `db/migration/audit/`에 해당 DB용 SQL을 추가합니다. 파일 번호는 DB별로 독립적입니다. 적용된 버전 파일은 유지하고 후속 변경은 새 버전 파일로 추가합니다.

V3는 app DB에 ui_screen·ui_menu·ui_screen_permission과 ui:screen:manage·ui:menu:manage 권한 정의를 추가합니다. 기존 같은 코드의 권한 정의는 유지하며 역할 연결·기본 화면·메뉴는 자동 생성하지 않습니다. 메뉴 API를 사용할 배포에서는 app V3를 먼저 적용하고 승인된 관리 역할에 필요한 권한을 연결해야 합니다. audit DB 스키마에는 영향을 주지 않습니다.

seed가 필요한 프로파일의 YAML에서 스키마·seed 경로를 함께 지정합니다. 아래 seed 경로는 예시이며 실제 SQL을 해당 위치에 준비해야 합니다.

```yaml
app:
  migration:
    enabled: true
    app-locations:
      - classpath:db/migration/app
      - classpath:db/seed/app
    audit-locations:
      - classpath:db/migration/audit
      - classpath:db/seed/audit
```

프로파일의 목록은 공통 목록 전체를 대체하므로 seed를 추가할 때 스키마 경로도 함께 적습니다. 파일 실행 순서는 목록 순서가 아니라 Flyway의 버전·반복 스크립트 규칙으로 결정됩니다. `R__이름.sql`은 처음 적용되거나 내용의 체크섬이 변경되면 실행됩니다. 같은 데이터를 다시 넣어도 중복되지 않도록 작성합니다.

현재 공통 테스트 계정은 `@Sql`이 `fixtures/auth-user.sql`을 읽어 준비합니다. 다중 위치 검증 테스트는 별도의 `fixtures/audit-migration`과 `fixtures/audit-seed`를 사용합니다.

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
