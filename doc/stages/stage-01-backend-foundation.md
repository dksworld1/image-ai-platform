# 1단계: 백엔드 프레임워크와 데이터 기반

2026-11-07: local/test에 별도 H2 파일 데이터소스 `audit`을 추가했습니다. local/test에서 `app`·`audit`별 Flyway 빈으로 독립 SQL 적용과 재실행을 검증합니다. [ADR-018](../decisions/ADR-018-per-datasource-migrations.md)

- 상태: 기반 구성 완료, 신규 업무용 app 데이터소스 추가 및 legacy 구성 유지
- 선행 단계: 없음
- 관련 문서: [아키텍처](../architecture/architecture.md), [API 계약](../api/api.md), [ADR-002](../decisions/ADR-002-backend-data-access-and-local-test-database.md), [ADR-005](../decisions/ADR-005-shared-dao-across-data-sources.md), [ADR-006](../decisions/ADR-006-backend-package-boundaries.md), [ADR-010](../decisions/ADR-010-app-datasource.md), [ADR-011](../decisions/ADR-011-remove-schema-auto-initialization.md)

## 목표와 범위

기능 단계들이 공통으로 사용할 백엔드 실행 기반과 데이터 접근 방식을 마련합니다. 데이터 소스 설정, SQL 매퍼, 트랜잭션 관리와 로컬 테스트용 데이터베이스 구성을 다룹니다. 신규 업무 기능의 기본 데이터소스는 `app`이며 기존 `catalog`·`archive` 및 foundation DAO는 legacy 호환을 위해 유지합니다. 현재 원칙은 [ADR-010](../decisions/ADR-010-app-datasource.md)을 따릅니다.

## 채택한 기술 방향

- 백엔드 Java 패키지는 `controller`, `service`, `dao`, `domain`의 역할 경계로 구성합니다. DAO 계약 인터페이스는 `dao` 바로 아래에, 구현체는 `dao.impl`에 둡니다. `domain`은 업무 모델·값 객체·도메인 규칙과 도메인에 필요한 업무 컴포넌트를 담습니다. HTTP 처리와 응답 매핑은 controller, 업무 흐름과 트랜잭션은 service, 저장소 접근은 dao에 둡니다. 아직 존재하지 않는 업무를 위해 빈 패키지나 클래스를 선행 생성하지 않습니다.
- DB 접근은 SQL 매퍼 방식을 사용합니다.
- SQL 매퍼 프레임워크는 MyBatis를 사용합니다.
- 신규 업무 `app` 및 legacy 데이터소스의 로컬·테스트 설정은 H2 파일 저장 모드를 사용합니다.
- SQL statement는 XML에 작성하고 각 `SqlSessionFactory`가 `classpath*:mapper/**/*.xml` 검색 규칙으로 로드합니다. DAO는 `SqlSessionTemplate`에서 namespace와 statement ID를 직접 호출합니다. MyBatis 자동 mapper scan을 끄고 `@Mapper` 인터페이스/프록시는 사용하지 않습니다.
- MyBatis 설정 경로는 `application.yaml`의 `mybatis.config-location`과 `mybatis.mapper-locations`에서 관리하고, `MyBatisConfigurationProperties`로 함께 바인딩해 각 `SqlSessionFactory` 생성에 전달합니다. MyBatis 옵션은 `config/mybatis-config.xml`에 둡니다.
- 다중 데이터소스는 라우팅 데이터소스를 사용하지 않습니다. 데이터소스마다 `SqlSessionFactory`와 Spring 관리 `SqlSessionTemplate`을 두고, 동일 DAO 인터페이스 및 구현 클래스로 구성한 별도 Spring 빈을 각각 연결합니다.
- 데이터소스, 세션 템플릿, 트랜잭션 매니저의 선택에 `@Primary`를 사용하지 않습니다. 의존성은 `@Qualifier`로 명시해 잘못된 DB가 자동 선택되지 않게 합니다.
- 공통 DAO 구현은 주입받은 `SqlSessionTemplate`로 MyBatis statement를 직접 호출합니다. raw `DefaultSqlSession` 인스턴스를 싱글턴 DAO에 주입해 공유하지 않습니다.
- 서비스는 `@Qualifier` 또는 명시적인 DAO 선택 계층으로 사용할 데이터소스의 DAO 빈을 지정합니다. DAO가 요청마다 암묵적으로 DB를 바꾸지 않습니다.
- 업무 서비스는 Spring `@Transactional`로 트랜잭션 경계를 선언합니다. 기반 테스트는 예외 발생 시 트랜잭션 롤백을 확인하도록 작성했습니다.
- Spring 실행 설정은 `prod`, `dev`, `local` 프로파일로 분리합니다. 자동화 테스트의 H2 설정은 별도의 테스트 프로파일에서 관리합니다.
- 로그 설정 파일은 `backend/src/main/resources/config/logger/` 아래 프로파일별로 둡니다. 각 Spring 프로파일에서 `logging.config`를 통해 대응 파일을 `classpath:config/logger/`로 참조합니다.
- 위 항목은 이 단계에서 구현한 기반 구성입니다. 운영 DB 설정과 운영 스키마 관리는 범위 밖입니다.

## Spring 설정 프로파일

| 프로파일 | 용도 | 데이터소스 설정 원칙 |
|---|---|---|
| `local` | 개발자 로컬 실행 | classpath의 `config/datasource/jdbc-local.yaml`에서 app과 catalog/archive H2 파일 DB를 가져옵니다. |
| `dev` | 공유 개발 환경 | 외부 설정 파일을 `spring.config.additional-location` 경로로 읽습니다. DB 제품과 값은 환경별 파일로 관리합니다. |
| `prod` | 운영 배포 환경 | 서버에서만 관리하는 외부 설정 파일을 `spring.config.additional-location` 경로로 읽습니다. 파일 자체와 비밀 값은 저장소에 두지 않습니다. |
| 테스트 프로파일 | 자동화 테스트 | classpath의 `config/datasource/jdbc-test.yaml`에서 app과 catalog/archive H2 파일 DB를 읽습니다. legacy probe 테이블은 테스트 fixture에서 준비합니다. |

프로파일별 설정은 환경 차이만 담고 공통 설정은 기본 설정으로 유지합니다. 기본 활성 프로파일은 `local`이며, Spring이 활성 프로파일에 해당하는 설정을 함께 적용합니다.

## 데이터소스 설정 파일

- 연결 주소, 계정, 암호, 풀 설정은 YAML 설정 파일에서 읽습니다. 개별 DB 연결값을 환경변수 여러 개로 전달하지 않습니다.
- 공통 설정은 `application.yaml`에 두고, 프로파일별 `application-{profile}.yaml`에서 `spring.config.import`로 JDBC 설정 파일을 가져옵니다.
- `application.yaml`은 `mybatis.config-location`으로 `classpath:config/mybatis-config.xml`, `mybatis.mapper-locations`로 매퍼 XML 패턴을 지정합니다. `mybatis-config.xml`은 설정만 포함하고 `<mappers>`는 비워 두며, 매퍼는 `setMapperLocations`로 로드합니다.
- JDBC 설정 파일은 `config/datasource/` 아래에 둡니다. 로컬 H2 설정은 `classpath:config/datasource/jdbc-local.yaml`을 사용합니다.
- 자동화 테스트의 H2 파일 DB 설정은 `classpath:config/datasource/jdbc-test.yaml`에 두고 테스트 프로파일에서 가져옵니다.
- dev/prod는 `--spring.config.additional-location`으로 외부 JDBC YAML을 읽습니다.
- `dev`와 `prod` 외부 YAML은 신규 업무 DB의 `app.datasource`와 legacy DB의 `app.datasources.catalog`, `app.datasources.archive`에 연결 설정을 둡니다. app은 신규 업무에서 필수입니다.
- 필수 외부 파일은 `optional:` 없이 지정합니다.
- 실제 연결값을 저장소에 두지 않습니다. 예시 파일을 둘 경우 값은 자리표시자로만 작성합니다.

## 신규 업무 app 및 legacy 기반 DAO 구성

```text
신규 업무 DAO
  └── appDataSource → appSqlSessionFactory → appSqlSessionTemplate → appTransactionManager

legacy foundation DAO
  ├── catalogFoundationProbeDao ── SqlSessionTemplate A
  │                                  └── SqlSessionFactory A ── catalogDataSource
  └── archiveFoundationProbeDao ── SqlSessionTemplate B
                                     └── SqlSessionFactory B ── archiveDataSource

각 SqlSessionFactory는 같은 XML statement namespace와 Mapper XML을 등록
```

신규 업무 기능은 `app` 키와 `appDataSource`, `appSqlSessionFactory`, `appSqlSessionTemplate`, `appTransactionManager`를 사용합니다. local app DB는 `backend/data/image-ai-app`, test app DB는 `backend/target/image-ai-platform-app-test` H2 파일입니다. 기존 foundation DAO는 `catalog`와 `archive`의 독립 H2 파일 및 전용 DAO 빈을 유지합니다. 기존 기능 호환을 위한 설정이며 신규 업무 기능은 사용하지 않습니다.

기존 foundation DAO의 구현 기준:

1. 신규 업무 기능은 `app` 데이터소스와 전용 `SqlSessionFactory`, `SqlSessionTemplate`, `PlatformTransactionManager`를 사용합니다. legacy foundation 데이터소스 구성은 보존합니다.
2. 공통 DAO 인터페이스를 구현하는 DAO 클래스는 `SqlSessionTemplate`을 생성자로 받습니다. 한 클래스를 데이터소스별로 각각 인스턴스화하고, 각 빈에 해당 DB 전용 템플릿을 연결합니다. Spring 빈 이름은 역할과 데이터소스 키를 식별하도록 정하고 `@Qualifier`로 주입합니다.
3. DAO는 주입된 템플릿으로 namespace와 statement ID를 직접 호출합니다. `@Mapper` 인터페이스나 Mapper 프록시는 등록하지 않습니다. 같은 statement ID와 파라미터·결과 계약을 각 데이터베이스가 지원해야 쿼리 정의를 그대로 공유할 수 있습니다.
4. 신규 서비스는 app DAO와 `appTransactionManager`를 사용합니다. 트랜잭션 매니저와 MyBatis 팩토리는 반드시 같은 `DataSource`에 연결합니다.
5. 공통 DAO 계약과 구현을 공유하더라도 스키마 또는 SQL 방언이 다르면 동일 XML이 그대로 실행되지 않을 수 있습니다. 차이가 확인되면 DB별 statement 변형이나 별도 매퍼 구현으로 분기하고 통합 검증합니다.

DAO에 주입하는 세션은 Spring 관리 `SqlSessionTemplate`로 합니다. raw `DefaultSqlSession`은 스레드 안전하지 않으므로 싱글턴 DAO에서 공유하지 않습니다. DAO가 `commit`, `rollback`, `close`를 직접 호출하지 않습니다.

이 구조는 한 번의 트랜잭션이 선택된 한 데이터소스에 참여하는 것을 기본으로 합니다. 한 업무 단위에서 둘 이상의 DAO 빈을 호출해도 DB 간 원자성이 자동 보장되지 않습니다. 다중 DB 원자성이 실제 요구되면 JTA/XA 또는 보상 트랜잭션을 별도로 설계합니다.

## 프로파일별 로그 설정

| Spring 프로파일 | 설정 파일 경로 | 로그 정책 방향 |
|---|---|---|
| `local` | `config/logger/logback-local.xml` | 로컬 개발 원인 분석을 돕는 콘솔 로그. 세부 로그 레벨은 구현 시 결정합니다. |
| `dev` | `config/logger/logback-dev.xml` | 개발 환경 진단에 필요한 로그. 세부 레벨과 출력 형식은 배포 환경과 함께 결정합니다. |
| `prod` | `config/logger/logback-prod.xml` | 운영 관측에 필요한 로그를 출력하고 민감 정보를 기록하지 않습니다. 세부 레벨, 구조화 형식 및 수집 경로는 운영 요구에 따라 결정합니다. |

각 `application-{profile}` 설정의 `logging.config`에 `classpath:config/logger/logback-{profile}.xml` 형식으로 경로를 지정합니다. Spring Boot는 `logging.config`로 classpath 로그 설정을 불러올 수 있습니다. 로그 초기화는 ApplicationContext 생성보다 먼저 일어나므로 경로는 초기 외부 설정에서 제공하고, `@PropertySource`로 뒤늦게 지정하지 않습니다. 공통 설정 재사용 방식은 구현 시 정하며, 설정 파일이 최종 패키지의 classpath에 포함되는지도 확인합니다.

## 핵심 개념과 원리

- 데이터 소스 설정은 연결 풀, 연결 정보, 환경별 설정을 캡슐화합니다. 비밀 정보는 코드에 두지 않고 환경별 설정으로 주입합니다.
- MyBatis 매퍼는 SQL 실행과 결과 매핑을 담당하며, 업무 규칙은 서비스 계층에 둡니다.
- 트랜잭션 경계는 하나의 업무 단위가 원자적으로 완료되어야 하는 범위를 기준으로 서비스 계층에 둡니다.
- 한 트랜잭션 안에서 예외가 발생했을 때 커밋 또는 롤백 동작을 명시하고, 외부 장시간 작업을 DB 트랜잭션에 무리하게 포함하지 않습니다.
- 로컬 테스트에서 파일 기반 H2를 쓰면 프로세스가 종료되어도 DB 상태가 남습니다. legacy probe 테이블은 앱에서 생성하지 않습니다.
- H2는 로컬 테스트 편의를 위한 설정이며 운영 DB 선택을 결정하지 않습니다. 운영 DB와 H2 간 SQL 차이도 검증 범위에 포함해야 합니다.

## 설계 범위

- Spring Boot와 MyBatis 설정 및 매퍼 검색 규칙
- `prod`, `dev`, `local` Spring 설정 프로파일과 테스트 프로파일의 경계
- 데이터 소스 및 연결 풀 설정, 프로파일별 외부 설정 주입
- `application.yaml` 및 프로파일별 YAML의 `spring.config.import` 규칙
- `config/datasource/` JDBC 설정 파일과 운영 `spring.config.additional-location` 경로 전달
- 라우팅 없는 DB별 DAO 빈, 공통 MyBatis 쿼리 재사용 및 명시적 빈 선택
- DB별 MyBatis 팩토리·세션 템플릿·트랜잭션 매니저의 대응 관계
- `config/` 내 프로파일별 로그 설정 파일과 classpath 참조
- 로그 레벨, 민감 정보 마스킹, 출력 형식과 운영 수집 경로
- 트랜잭션의 시작·커밋·롤백 경계와 전파 규칙
- DB 연결 오류와 트랜잭션 실패의 로깅·오류 응답 원칙

## 결정 기록

- 채택: SQL 매퍼 사용, 프레임워크는 MyBatis
- 채택: 로컬 테스트 DB로 H2 파일 저장 모드 사용
- 채택: 실행 프로파일로 `prod`, `dev`, `local` 사용, 자동화 테스트에는 별도 테스트 프로파일 사용
- 채택: JDBC 연결값은 YAML 설정 파일에서 읽고 운영에서는 `spring.config.additional-location`로 외부 파일 경로를 지정
- 채택: 라우팅 데이터소스 없이 데이터소스별 DAO 빈을 만들고 공통 DAO 구현·쿼리를 공유
- 채택: 프로파일별 로그 설정을 `config/`에 두고 `logging.config`의 classpath 경로로 참조
- 채택: 트랜잭션 경계를 업무 서비스 단위로 관리
- 채택: 각 DAO 빈은 해당 데이터소스의 `SqlSessionTemplate`과 트랜잭션 매니저를 사용
- 채택: 상세 구조와 제한은 [ADR-005](../decisions/ADR-005-shared-dao-across-data-sources.md)에 기록
- 미결정: 운영 DB와 운영 환경의 데이터 소스 설정

## 완료 조건과 검증 결과

- 완료: JDBC 연결 정보와 HikariCP 풀 설정은 YAML 설정 파일에서 읽습니다. 운영·개발 환경은 `spring.config.additional-location`로 외부 파일을 지정합니다.
- 완료: MyBatis 설정 경로는 `application.yaml`에서 `MyBatisConfigurationProperties`로 바인딩해 app 및 legacy 세션 팩토리에 적용합니다. `mybatis-config.xml`의 옵션 설정과 mapper-locations 경로를 별도로 전달합니다. Mapper XML은 SQL 실행과 결과 매핑을 담당하고 DAO는 `SqlSessionTemplate`으로 statement를 직접 호출합니다. 업무 규칙은 서비스 계층이 담당합니다.
- 완료: Spring 트랜잭션 매니저와 예외 시 롤백을 확인하는 자동 테스트를 추가했습니다.
- 완료: `SchemaInitializationConfiguration` 클래스와 `schema-*.sql` 초기화 파일은 제거했습니다.
- 완료: 신규 업무용 `appDataSource`와 전용 MyBatis 및 트랜잭션 빈을 추가했습니다. 기존 `catalogFoundationProbeDao`, `archiveFoundationProbeDao` 기반 구성은 그대로 보존했습니다.
- 완료: 기존 catalog/archive DAO와 트랜잭션 매니저를 유지해 데이터 격리 및 롤백 검증 경계를 보존하고, 신규 업무에는 `appTransactionManager`를 사용합니다.
- 완료: 기본 프로파일은 `local`이며 `dev`, `prod`, `test` 프로파일별 설정에서 JDBC YAML을 가져옵니다. dev/prod 외부 JDBC YAML은 실행 옵션으로 지정합니다.
- 완료: 각 local/dev/prod 프로파일은 전용 `config/logger/` Logback XML을 classpath로 지정합니다. 패키징 결과에서 YAML, JDBC, Logback 파일을 확인했습니다.
- 운영 DB 제품별 SQL 호환성과 legacy catalog/archive 스키마 관리는 별도 설계합니다.
- 검증: Java 21 세션(`JAVA_HOME=C:\Program Files\Java\jdk-21`)에서 `mvn test` — 성공. catalog/archive 파일 DB 격리, DAO 구현 공유, 각 트랜잭션 매니저의 롤백을 확인했습니다.
- 이전 Maven 컴파일 시 사용자 캐시 잠금 파일 접근이 거부된 바 있어 빌드 검증도 보류 상태입니다.

현재 마이그레이션은 DB별 다중 위치와 seed 적용을 지원하며 app/audit 위치 목록 생략·빈 목록을 허용합니다. 구현 및 검증 기록은 [DB 마이그레이션 설정](../operations/database-migration.md)을 참고합니다.
