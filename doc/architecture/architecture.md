# 아키텍처

상태: 초안 기준선  
갱신일: 2026-11-02

## 개요

단일 저장소 안에 독립 실행 가능한 웹 클라이언트와 REST API를 둡니다. 현재 업무 기능은 정해지지 않아 최소한의 연결 확인 흐름만 구현합니다.

```text
브라우저 :4173 (정적 미리보기) 또는 :5173 (개발 서버)
    └── /api/* → Vite 프록시 → Spring Boot :8080
                               └── /api/health
```

## 구성 요소

- `frontend/`: 화면과 API 호출 모듈. 개발 및 정적 미리보기 중 `/api` 요청은 Vite 프록시를 통해 백엔드로 전달합니다.
- `backend/`: Java 21, Spring Boot 4, MVC 기반 API. 하나의 애플리케이션과 배포 단위를 유지하는 모듈러 모놀리스입니다. 공통 기반 코드는 `foundation`, 업무 기능은 `auth`와 `dataset` 기능 모듈로 구성합니다.
- `doc/`: 제품 설계, 현재 아키텍처·API 계약, 기능 구현 단계 및 의사결정 기록.

## 로컬 실행 경계

- 웹 앱 기본 주소: `http://localhost:5173`
- 정적 미리보기 기본 주소: `http://localhost:4173`
- API 기본 주소: `http://localhost:8080`
- 개발 프록시 대상: `API_PROXY_TARGET` 환경 변수 또는 `http://localhost:8080`
- 정적 미리보기에서도 `/api` 요청을 같은 백엔드로 전달합니다.
- 브라우저에서 직접 API를 호출할 때 허용할 출처: `CORS_ALLOWED_ORIGIN` 또는 `http://localhost:5173`

백엔드는 `application.yaml` 및 프로파일별 YAML에서 데이터소스 설정을 가져오며 HikariCP와 MyBatis XML을 사용합니다. 신규 업무 기본 데이터소스는 `app`이며 `app.datasource` 아래에서 설정합니다. 기존 `catalog`·`archive` 데이터소스와 foundation DAO는 호환성을 위해 유지합니다. MyBatis 설정은 `MyBatisConfigurationProperties`를 통해 세션 팩토리에 전달합니다. DB 변경은 Flyway 버전 SQL과 그룹별 [DB 정의서](../db-schema/tables/README.md)로 관리합니다. local/test는 시작 시 마이그레이션하고 dev/prod는 배포 단계에서 전용 실행 모드로 적용합니다. 테스트 데이터는 `db/seed/`에서 관리하고 test 프로파일에만 적용합니다. `catalog`·`archive`는 Flyway 범위 밖입니다. 기본 프로파일은 `local`이고 dev/prod는 `APP_CONFIG_FILE`에서 DB 설정을 읽습니다. 프로파일별 Logback 설정은 `backend/src/main/resources/config/logger/`에 둡니다. 상세 사항은 [ADR-012](../decisions/ADR-012-flyway-app-migrations.md)을 참고합니다.

프로파일별 로그 설정은 `backend/src/main/resources/config/logger/logback-{profile}.xml`에 두고 각 프로파일의 `logging.config`에서 classpath 경로로 참조합니다. 세부 로그 정책은 운영 요구에 따라 조정합니다.

신규 기능은 `appDataSource`, `appSqlSessionFactory`, `appSqlSessionTemplate`, `appTransactionManager`를 사용합니다. legacy foundation probe는 기존 `catalog`·`archive` 전용 DAO와 세션 구성으로 남아 있습니다. 데이터소스별로 `@Qualifier`를 명시하고, 여러 DB에 걸친 트랜잭션은 구성하지 않습니다. 상세 설계는 [ADR-010](../decisions/ADR-010-app-datasource.md)을 따릅니다.

## 확장 규칙

- 컨트롤러는 HTTP 요청/응답에 집중하고, 업무 규칙은 서비스 계층으로 둡니다.
- 업무 모듈 내부의 Java 패키지는 `controller`, `service`, `dao`, `domain` 역할로 나눕니다. DAO 계약 인터페이스는 모듈 `dao` 바로 아래에 두고 구현체는 `dao.impl`에 둡니다. `domain`에는 업무 모델, 값 객체, 도메인 규칙과 도메인 업무에 필요한 컴포넌트를 배치하며, 데이터 접근 세부사항은 두지 않습니다.
- 공통 기반 코드는 `com.imageaiplatform.foundation` 아래 역할별 하위 패키지에 둡니다. 현재 설정은 `foundation.config`, 상태 확인은 `foundation.controller`, 기반 DAO 계약 및 구현은 `foundation.dao`와 `foundation.dao.impl`에 있습니다.
- API 입력/출력은 명시적인 DTO로 정의하고 내부 도메인 모델을 그대로 노출하지 않습니다.
- 새 외부 의존성과 신뢰 경계는 관련 설계 문서 및 ADR에 기록합니다.

## 모듈 경계

- 업무 기능 모듈은 `com.imageaiplatform.auth`(인증·권한)과 `com.imageaiplatform.dataset`(이미지 데이터셋)입니다. 각 모듈 안에서 `controller`, `service`, `dao`, `domain` 역할을 나눕니다.
- DAO 계약은 모듈의 `dao` 바로 아래, 구현은 `dao.impl` 아래에 둡니다. 도메인 모델과 업무 규칙은 해당 모듈의 `domain`에 둡니다.
- 모듈 간 업무 호출은 상대 모듈의 서비스 경계를 사용합니다. 다른 모듈의 DAO, 구현 세부사항 또는 도메인 객체를 직접 참조하지 않습니다. HTTP DTO와 모듈 내부 모델을 분리합니다.
- 애플리케이션 진입점은 `com.imageaiplatform.Application`에 둡니다. 공통 설정·헬스체크·기반 DAO는 `foundation`이 소유하며, 업무 책임은 각 기능 모듈이 소유합니다.
- 현재는 Java 패키지 경계만 사용합니다. Maven 서브모듈이나 별도 배포 단위는 도입하지 않습니다.

## 인증

- 인증 모듈은 Spring Security 무상태 필터 체인과 JWT access token을 사용합니다. 로그인은 아이디·BCrypt 비밀번호 해시를 확인하고 HS256 JWT를 발급합니다.
- 계정 조회는 `app` 데이터소스의 `auth_user` 테이블을 사용합니다. local/test는 YAML의 비운영 전용 키를 쓰고 dev/prod는 `JWT_SECRET` 환경 변수에서 읽습니다. 기본 유효기간은 15분입니다.
- 로그인 및 헬스체크 외 API는 인증을 요구합니다. 상세 HTTP 계약은 [API 계약](../api/api.md)을 참고합니다.

## DB 테이블 정의

DB 테이블은 `doc/db-schema/tables/` 아래 업무 그룹별 정의서로 관리합니다. 현재 테이블 정의는 [정의서 색인](../db-schema/tables/README.md)을 참고합니다. `app` 적용 이력은 Flyway가 관리합니다.
