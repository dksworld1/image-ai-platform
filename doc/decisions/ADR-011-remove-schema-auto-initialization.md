# ADR-011: 애플리케이션 스키마 자동 초기화 제거

- 상태: 대체됨 ([ADR-012 Flyway 기반 app DB 마이그레이션](ADR-012-flyway-app-migrations.md))
- 날짜: 2026-11-02
- 관련 기록: [ADR-002 MyBatis와 로컬 테스트용 H2 파일 DB 사용](ADR-002-backend-data-access-and-local-test-database.md), [ADR-010 신규 업무 기본 app 데이터소스](ADR-010-app-datasource.md)

## 맥락

테이블 그룹별 `SchemaInitializationConfiguration`과 `schema-*.sql` 파일을 애플리케이션이 실행하는 방식은 테이블이 늘 때 설정 코드와 초기화 경로도 추가하게 됩니다. 운영을 포함한 버전별 스키마 진화와 배포 이력을 위한 방식을 아직 결정하지 않았습니다.

## 결정

- 애플리케이션 시작 시 테이블을 만드는 `FoundationSchemaInitializationConfiguration`, `AuthSchemaInitializationConfiguration`과 모든 `schema-*.sql` 파일을 제거합니다.
- local, test, dev, prod 모두 애플리케이션이 업무 테이블을 자동 생성하지 않습니다.
- 테스트가 foundation probe 테이블을 필요로 할 경우 테스트 fixture에서만 생성합니다.
- 테이블 정의는 `doc/db-schema/tables/`의 그룹별 정의서에 계속 기록합니다.
- `foundation_probe`는 legacy foundation 테스트용이며 `app` DB 마이그레이션에는 포함하지 않습니다.
- 당시에는 Flyway 등 버전 기반 마이그레이션 도구의 도입을 후속 TODO로 남겼습니다. 이후 app 데이터소스에 대한 방침은 ADR-012에서 정했습니다.

## 결과

- 애플리케이션 초기화 과정에서 스키마 DDL을 실행하지 않습니다.
- 인증 API를 사용하려면 `app.auth_user`를 포함한 필수 테이블을 사전에 만들어야 합니다.
- H2 파일 DB를 새로 만든 개발 환경도 같은 사전 프로비저닝이 필요합니다. 기존 DB와 데이터를 삭제하거나 이전하지 않습니다.
- 테스트 전용 fixture DDL은 운영 애플리케이션 설정에 포함되지 않습니다.
