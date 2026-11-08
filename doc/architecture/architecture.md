# 아키텍처

상태: 초안 기준선  
갱신일: 2026-11-08

## 개요

단일 저장소 안에 독립 실행 가능한 웹 클라이언트와 REST API를 둡니다. 현재 업무 기능은 정해지지 않아 최소한의 연결 확인 흐름만 구현합니다.

```text
브라우저 :4173 (정적 미리보기) 또는 :5173 (개발 서버)
    └── /api/* → Vite 프록시 → Spring Boot :8080
                               └── /api/health
```

## 구성 요소

- `frontend/`: 화면과 API 호출 모듈. 개발 및 정적 미리보기 중 `/api` 요청은 Vite 프록시를 통해 백엔드로 전달합니다.
- `backend/`: Java 21, Spring Boot 4, MVC 기반 API. 하나의 애플리케이션과 배포 단위를 유지하는 모듈러 모놀리스입니다. 공통 기반 코드는 `foundation`, 업무 기능은 `auth`, `ui`, `dataset` 기능 모듈로 구성합니다.
- `doc/`: 제품 설계, 현재 아키텍처·API 계약, 기능 구현 단계 및 의사결정 기록.

## 로컬 실행 경계

- 웹 앱 기본 주소: `http://localhost:5173`
- 정적 미리보기 기본 주소: `http://localhost:4173`
- API 기본 주소: `http://localhost:8080`
- Vite 개발 서버와 미리보기 서버는 `/api` 요청을 `bin/local.settings.ps1`의 `BackendPort`로 전달합니다(기본값 8080).
- 정적 미리보기에서도 `/api` 요청을 같은 백엔드로 전달합니다.
- 브라우저에서 직접 API를 호출할 때 허용할 출처: 쉼표로 구분한 `app.cors.allowed-origin`. local 기본값은 `http://localhost:5173`, `http://localhost:4173`이며 그 외 프로필의 공통 기본값은 `http://localhost:5173`입니다.

백엔드는 `application.yaml` 및 프로파일별 YAML에서 데이터소스 설정을 가져오며 HikariCP와 MyBatis XML을 사용합니다. 신규 업무 기본 데이터소스는 `app`이며 `datasource.app` 아래에서 설정합니다. 기존 `catalog`·`archive` 데이터소스와 foundation DAO는 호환성을 위해 유지합니다. MyBatis 설정은 `MyBatisConfigurationProperties`를 통해 세션 팩토리에 전달합니다. 기본 프로파일은 `local`이고 dev/prod는 `spring.config.additional-location`에서 DB 설정을 읽습니다. 프로파일별 Logback 설정은 `backend/src/main/resources/config/logger/`에 둡니다.

프로파일별 로그 설정은 `backend/src/main/resources/config/logger/logback-{profile}.xml`에 두고 각 프로파일의 `logging.config`에서 classpath 경로로 참조합니다. 세부 로그 정책은 운영 요구에 따라 조정합니다.

신규 기능은 `appDataSource`, `appSqlSessionFactory`, `appSqlSessionTemplate`, `appTransactionManager`를 사용합니다. legacy foundation probe는 기존 `catalog`·`archive` 전용 DAO와 세션 구성으로 남아 있습니다. 데이터소스별로 `@Qualifier`를 명시하고, 여러 DB에 걸친 트랜잭션은 구성하지 않습니다. 상세 설계는 [ADR-010](../decisions/ADR-010-app-datasource.md)을 따릅니다.

## 설정 관리 원칙

local/test는 `app`과 별도 H2 파일을 사용하는 `audit` 데이터소스를 구성합니다. `DatabaseMigrationConfiguration`이 각 데이터소스를 해당 클래스패스 SQL 위치 목록에 연결합니다. local/test에서 활성화되며 최종 위치 목록이 빈 DB는 적용을 건너뜁니다. [설정 방법](../operations/database-migration.md) [ADR-018](../decisions/ADR-018-per-datasource-migrations.md)

Spring 초기화 전 필요한 런타임 설정은 클래스패스 `config/runtime/` 바로 아래의 주제별 UTF-8 `.properties` 파일과 중앙 로더로 관리합니다. `foundation.config`의 `RuntimeSettingsLoader`가 파일을 읽고 불변 `RuntimeSettings`를 `RuntimeConfig`의 정적 필드 초기화 시 생성해 정적 `settings()`로 제공합니다. `main()`의 초기화 호출은 사용하지 않습니다. 실제 설정 항목은 아직 없어 현재 설정 객체는 필드 없는 record입니다. 새 항목은 명시적 필드와 중앙 로더 매핑을 함께 추가합니다. 상세 설계는 [ADR-014](../decisions/ADR-014-static-runtime-settings.md)를 따릅니다.

백엔드 애플리케이션 설정은 Spring YAML을 우선 사용합니다. 프로젝트 설정에 환경 변수 사용을 금지합니다. YAML은 가능한 한 주제별로 분리해 `spring.config.import`로 조합하며 비밀값이 포함된 파일은 저장소 밖에서 관리합니다. [ADR-013](../decisions/ADR-013-topic-based-spring-yaml.md)을 따릅니다.

현재 배포 설정은 [배포 및 서버 설정 운영](../operations/deployment.md)을 따릅니다.

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

프런트 인덱스는 현재 로그인 화면을 먼저 표시합니다. `POST /api/auth/token` 성공 후 기존 시작 화면으로 전환하며 토큰 응답은 `App`의 메모리 상태에만 보관합니다. 새로고침 시 재로그인이 필요합니다. 내비게이션 조회에는 로그인 토큰을 전달하고 허용 메뉴 계층을 표시합니다. 미구현 관리 화면은 준비 중으로 표시합니다. 나머지 업무 API 토큰 전달·로그아웃·만료 처리와 실제 화면 라우팅은 후속 구현입니다. [ADR-020](../decisions/ADR-020-login-entry-memory-token.md), [프런트 인증 단계](../stages/front/stage-02-authentication.md)를 참고합니다.

- 인증 모듈은 Spring Security 무상태 필터 체인과 JWT access token을 사용합니다. 로그인은 아이디·BCrypt 비밀번호 해시를 확인하고 HS256 JWT를 발급합니다.
- 계정 조회는 `app` 데이터소스의 `auth_user` 테이블을 사용합니다. local/test는 YAML의 비운영 전용 키를 쓰고 dev/prod는 외부 YAML의 `app.auth.jwt.secret`에서 읽습니다. 기본 유효기간은 15분입니다.
- JWT 필터가 인증을 등록하고 전역 MVC 인가 인터셉터가 메서드의 `@PublicApi`·`@RequirePermission`을 읽어 공개·인증 전용·세부 권한 필요 요청을 판정합니다. 필터 체인의 URL별 인가 목록은 제거하고 MVC 인터셉터에서 기본 거부합니다. 어노테이션 없는 핸들러와 정적 리소스 핸들러도 거부합니다. 비-MVC 서블릿을 추가할 때는 별도의 보안 적용이 필요합니다. 상세 HTTP 계약은 [API 계약](../api/api.md)을 참고합니다.
- `AuthorizationService`는 실제 사용자 DAO와 `MyBatisEffectivePermissionDao`를 통해 계정 상태와 명시 권한을 확인합니다. API 인터셉터에 연결되어 비공개 API마다 활성 계정과 필요한 세부 권한을 확인합니다. 공통 스키마는 역할·사용자 권한 할당을 생성하지 않습니다. local 프로파일만 별도 app seed로 admin 계정·admin 역할·모든 기존 권한 연결을 준비하며 시작·관리 메뉴 메타데이터는 app 공통 seed에서 준비합니다. [ADR-022](../decisions/ADR-022-local-app-seed.md)를 따릅니다. 정책과 후속 작업은 [권한 설계 제안](../proposals/authorization-design-proposal.md)을 참고합니다.

## DB 테이블 정의

DB 테이블은 `doc/db-schema/tables/` 아래 업무 그룹별 정의서로 관리합니다. 현재 테이블 정의는 [정의서 색인](../db-schema/tables/README.md)을 참고합니다.

## 화면·메뉴

ui 모듈은 app DB의 ui_screen·ui_menu·ui_screen_permission을 MyBatis로 관리합니다. 권한 조회는 AuthorizationService.effectivePermissions를 호출해 auth 서비스 경계를 유지합니다. 조회는 화면·조건·메뉴를 일괄 읽어 Set/Map으로 계산하고 공개 내비게이션에는 허용된 화면과 메뉴만 반환합니다. 관리 API는 ui:screen:manage·ui:menu:manage로 구분하며 역할에 자동 부여하지 않습니다. 화면·조건 교체와 메뉴 수정은 app 트랜잭션으로 처리하고 메뉴 쓰기는 기존 메뉴 행 잠금으로 동시 부모 변경을 조율합니다. 상세 계약은 [메뉴·화면 API](../api/ui.md)를 참고하세요.
