# ADR-005: 라우팅 없는 다중 데이터소스와 공통 DAO 구현

- 상태: 채택 (legacy foundation DAO에 한정)
- 날짜: 2026-11-01
- 관련 단계: [1단계 백엔드 프레임워크와 데이터 기반](../plans/backend/stage-01-backend-foundation.md)

## 맥락

여러 데이터소스에 같은 업무 쿼리를 실행할 수 있어야 합니다. 요청 컨텍스트에 따라 연결을 암묵적으로 바꾸는 라우팅 데이터소스 대신, 사용하는 데이터소스를 DAO 빈으로 명시하고 쿼리 정의와 DAO 구현의 중복을 줄이는 구성이 필요합니다.

## 대안

- `AbstractRoutingDataSource` 한 개와 라우팅 컨텍스트를 두고 하나의 DAO로 접근합니다.
- 데이터소스마다 DAO 구현과 SQL을 따로 작성합니다.
- 데이터소스마다 MyBatis 세션 구성 및 DAO 빈을 두고 동일 DAO 인터페이스·구현과 호환되는 XML statement를 공유합니다.

## 당시 결정

이 결정은 기존 foundation의 `catalog`·`archive` DAO 구성에 계속 적용합니다. 신규 업무 기능의 기본 데이터소스 선택은 [ADR-010](ADR-010-app-datasource.md)을 따릅니다.

- `AbstractRoutingDataSource`를 사용하지 않습니다.
- 각 데이터소스에 고유한 `SqlSessionFactory`, `SqlSessionTemplate`, Spring 트랜잭션 매니저를 구성합니다.
- 데이터소스 선택에는 `@Primary`를 사용하지 않고 각 의존성을 `@Qualifier`로 지정합니다.
- 공통 DAO 인터페이스와 같은 DAO 구현 클래스를 각 데이터소스 전용 `SqlSessionTemplate`에 연결한 별도 Spring 빈으로 등록합니다.
- DAO 구현체의 주입 타입은 Spring 관리 `SqlSessionTemplate`로 합니다. 서비스는 `@Qualifier` 또는 명시적인 선택 계층으로 DAO 빈을 선택합니다.
- 각 세션 팩토리는 같은 XML statement namespace와 파일을 읽습니다. Spring Boot MyBatis 자동 설정의 mapper scan은 끄고, DAO는 `SqlSessionTemplate`의 statement ID 호출을 사용합니다. `@Mapper` 인터페이스나 mapper proxy는 사용하지 않습니다. 데이터베이스별 스키마와 SQL 방언이 호환될 때 쿼리 statement를 그대로 공유합니다.
- 트랜잭션은 한 데이터소스를 대상으로 합니다. 여러 DB에 걸친 원자성이 필요해지면 JTA/XA 또는 보상 트랜잭션을 별도 설계합니다.

## 결과

- 데이터소스 선택이 Spring 빈 구성과 서비스 주입에서 명시적이며, 같은 DAO 코드와 쿼리의 재사용이 가능합니다.
- 데이터소스를 추가할 때 관련 팩토리·템플릿·트랜잭션 매니저 및 DAO 빈 구성이 함께 필요합니다.
- DAO 빈을 잘못 선택하면 의도하지 않은 DB를 호출할 수 있으므로 빈 이름과 `@Qualifier` 규칙을 일관되게 적용하고 검증해야 합니다.
- 스키마나 DB 제품이 달라 공통 SQL을 실행할 수 없는 경우 DB별 statement 또는 매퍼 변형이 필요합니다.
- 여러 데이터소스의 작업을 하나의 원자적 트랜잭션으로 처리하지 않습니다.
- 구현은 `catalogDataSource`/`archiveDataSource`, 각 데이터소스 전용 `SqlSessionFactory`, `SqlSessionTemplate`, 트랜잭션 매니저와 `catalogFoundationProbeDao`/`archiveFoundationProbeDao` 빈으로 구성했습니다. 각 세션 팩토리는 `classpath*:mapper/**/*.xml`을 읽고, 같은 DAO 구현체의 각 인스턴스는 전용 템플릿에서 같은 namespace의 statement ID를 직접 호출합니다. 로컬과 테스트의 두 DB는 각각 H2 파일을 사용합니다.
- 자동 검증은 동일 DAO 구현체의 두 인스턴스가 서로 다른 DB에 기록하는지, 각각의 트랜잭션 매니저가 롤백을 적용하는지 확인합니다.
