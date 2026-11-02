# ADR-012: Flyway 기반 app DB 마이그레이션

- 상태: 채택
- 날짜: 2026-11-02
- 관련 기록: [ADR-010 신규 업무 기본 app 데이터소스](ADR-010-app-datasource.md), [ADR-011 스키마 자동 초기화 제거](ADR-011-remove-schema-auto-initialization.md)

## 맥락

테이블별 `SchemaInitializationConfiguration`과 `schema-*.sql` 파일을 제거한 뒤, DB 변경을 이력과 함께 반복 적용할 방법이 필요합니다. 신규 업무 기본 DB는 `app`이며 기존 `catalog`·`archive` 데이터소스는 foundation DAO에서 계속 사용합니다.

## 결정

- `spring-boot-starter-flyway`를 사용하고 Flyway 적용 범위는 `app` 데이터소스로 한정합니다. `@FlywayDataSource`로 기본 데이터소스를 명시합니다.
- 마이그레이션은 `backend/src/main/resources/db/migration/`의 버전 SQL 파일로 작성합니다. 적용된 파일은 수정하지 않고 변경마다 다음 버전을 추가합니다.
- 테스트용 데이터는 `backend/src/main/resources/db/seed/`의 Flyway 반복 SQL 파일(`R__...sql`)로 관리하고 `test` 프로파일에서만 migration 위치와 함께 적용합니다. 이 위치에는 테스트 데이터만 두며 local/dev/prod에서는 읽지 않습니다.
- 첫 파일 `V1__create_auth_user.sql`은 `auth_user` 테이블만 생성합니다. `foundation_probe`는 legacy foundation 테스트용이며 app 업무 스키마에 포함하지 않습니다.
- DB를 초기화한 상태에서 시작하므로 baseline은 사용하지 않습니다. Flyway의 `clean`은 비활성화합니다.
- local/test는 앱 시작 시 마이그레이션합니다. dev/prod의 일반 앱 실행은 마이그레이션을 건너뜁니다.
- dev/prod는 동일한 Spring 설정을 사용하는 마이그레이션 전용 실행 모드로 배포 단계에서 마이그레이션합니다. 이 모드는 웹 서버와 legacy 데이터소스 및 로그인·보안 서비스 빈을 시작하지 않습니다.
- Flyway 이력 테이블은 대상 app DB의 기본 스키마에 생성됩니다.

## 결과

- app의 스키마 변경과 적용 순서를 버전 파일 및 Flyway 이력 테이블로 확인할 수 있습니다.
- 새 환경에는 첫 migration이 실행되어 `auth_user`가 만들어집니다. 계정 행은 별도로 프로비저닝합니다.
- `test` 프로파일은 `test-user` 샘플 계정을 추가합니다. 암호는 테스트 전용 BCrypt 해시이며 운영 계정으로 사용하지 않습니다.
- dev/prod 배포는 앱 배포 전에 마이그레이션 전용 명령을 성공시켜야 합니다.
- catalog/archive는 Flyway로 관리하지 않으며 기존 foundation DAO용 DB 스키마 프로비저닝은 별도로 유지합니다.
