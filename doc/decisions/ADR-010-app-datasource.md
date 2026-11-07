# ADR-010: 신규 업무 기본 app 데이터소스와 legacy 구성 유지

- 상태: 채택
- 날짜: 2026-11-02
- 보완: [ADR-005 라우팅 없는 다중 데이터소스와 공통 DAO 구현](ADR-005-shared-dao-across-data-sources.md)
- 관련 기록: [ADR-004 데이터소스 설정 파일](ADR-004-datasource-config-files.md), [ADR-009 아이디·비밀번호 로그인과 JWT](ADR-009-username-password-jwt.md)

## 맥락

새 업무 기능에 사용할 기본 데이터소스 이름과 설정 경계를 정해야 합니다. 기존 `catalog`·`archive` 구성은 foundation DAO와 검증 코드가 이미 사용하므로 이번 변경에서 제거할 이유가 없습니다. 반면 신규 업무가 이 둘 중 하나를 우연히 고르지 않도록 명시적인 공통 데이터소스가 필요합니다.

## 결정

- 신규 업무 기능의 기본 데이터소스 이름은 `app`으로 정합니다. Spring 빈은 `appDataSource`, `appSqlSessionFactory`, `appSqlSessionTemplate`, `appTransactionManager`로 구성합니다.
- 아이디·비밀번호 인증 기능과 이후 신규 업무 테이블은 `app` 데이터소스에 둡니다. 새 기능은 `@Qualifier("appSqlSessionTemplate")` 또는 `appTransactionManager`를 사용합니다.
- `catalog`·`archive` 데이터소스 및 foundation DAO는 기존 코드와 호환성을 위해 보존합니다. 기존 코드는 기존 데이터소스를 계속 사용할 수 있지만 신규 업무 기능은 사용하지 않습니다.
- local의 `app`은 `image-ai-app` H2 파일을 사용합니다. 기존 catalog/archive 파일은 보존하며 자동 복사·삭제·통합하지 않습니다. test도 app 전용 H2 파일을 사용합니다.
- dev/prod 외부 JDBC 설정은 `app.datasource`와 legacy `app.datasources.catalog`, `app.datasources.archive`를 정의합니다.
- DB 테이블을 추가·변경·삭제하면 같은 변경에서 `doc/db-schema/tables/`의 기능별 정의서를 갱신합니다. 이미 존재하는 `auth_user`와 `foundation_probe` 정의서도 소급 작성합니다.
- `foundation_probe`는 legacy foundation DAO의 연결 검증용으로만 유지하며, 신규 업무 `app` 스키마에는 포함하지 않습니다.
- 스키마 자동 초기화 설정과 `schema-*.sql` 파일은 제거합니다.

## 결과

- 신규 업무 기능은 데이터소스 선택이 `app`으로 일관됩니다.
- 기존 foundation DAO 검증을 유지하면서 새 업무 테이블과 기존 catalog/archive 저장소를 분리합니다.
- local에서 기존 catalog/archive 데이터는 기존 파일에 그대로 남습니다. 신규 업무 데이터는 `image-ai-app` 파일에 저장되며 legacy DB에서 자동 이전하지 않습니다.
