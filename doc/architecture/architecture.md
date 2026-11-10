# 아키텍처

## 권한 검사 경계 변경 — 2026-11-09 확정

다중 권한 업무는 별도 상위 서비스 빈이 각 보호 서비스를 생성자 주입받아 호출합니다. 같은 객체 내부 호출로 프록시를 우회하지 않습니다. [작성 가이드라인](../plans/authorization-design-proposal.md#다중-권한-서비스-작성-가이드라인)을 따릅니다.

제한이 필요한 서비스 메서드에만 `@PreAuthorize`를 적용하며 모든 서비스에 강제하지 않습니다. 컨트롤러 퍼미션 체크는 완전히 제거하고 앞으로도 추가하지 않습니다. 서비스는 단일 계층으로 유지하며 여러 서비스 조합은 필요한 경우 업무별 상위 서비스에서 호출합니다. 서비스 Method Security 전환을 구현했으며 기존 관리 API의 필요 권한과 401·403·500 오류 계약은 유지합니다.

[ADR-026](../decisions/ADR-026-service-permission-boundary.md)에 따라 서비스 권한 검사를 구현했습니다. 이전 컨트롤러 권한 선언 의무·선언 누락 기본 거부·서비스 경계 보류 방침은 대체되었습니다. 비동기 인증 전달은 별도 보류 상태입니다.

상태: 초안 기준선  
갱신일: 2026-11-09

## 개요

단일 저장소 안에 독립 실행 가능한 웹 클라이언트와 REST API를 둡니다. 인증과 데이터셋 생성 업무를 구현했으며 목록·상세·이미지 업무는 후속 작업입니다.

```text
브라우저 :4173 (정적 미리보기) 또는 :5173 (개발 서버)
    └── /api/* → Vite 프록시 → Spring Boot :8080
                               └── /api/health
```

## 구성 요소

- `frontend/`: 화면과 API 호출 모듈. 개발 및 정적 미리보기 중 `/api` 요청은 Vite 프록시를 통해 백엔드로 전달합니다.
- `backend/`: Java 21, Spring Boot 4, MVC 기반 API. 하나의 애플리케이션과 배포 단위를 유지하는 모듈러 모놀리스입니다. 공통 기반 코드는 `foundation`, 업무 기능은 `auth`, `dataset` 기능 모듈로 구성합니다.
- `doc/`: 제품 설계, 현재 아키텍처·API 계약, 기능 구현 단계 및 의사결정 기록.

## 로컬 실행 경계

- 웹 앱 기본 주소: `http://localhost:5173`
- 정적 미리보기 기본 주소: `http://localhost:4173`
- API 기본 주소: `http://localhost:8080`
- Vite 개발 서버와 미리보기 서버는 `/api` 요청을 `bin/local.settings.ps1`의 `BackendPort`로 전달합니다(기본값 8080).
- 정적 미리보기에서도 `/api` 요청을 같은 백엔드로 전달합니다.
- 브라우저에서 직접 API를 호출할 때 허용할 출처: 쉼표로 구분한 `app.cors.allowed-origin`. local 기본값은 `http://localhost:5173`, `http://localhost:4173`, `http://127.0.0.1:5173`, `http://127.0.0.1:4173`이며 그 외 프로필의 공통 기본값은 `http://localhost:5173`입니다.

백엔드는 `application.yaml` 및 프로파일별 YAML에서 데이터소스 설정을 가져오며 HikariCP와 MyBatis XML을 사용합니다. 신규 업무 기본 데이터소스는 `app`이며 `datasource.app` 아래에서 설정합니다. 기존 `catalog`·`archive` 데이터소스와 foundation DAO는 호환성을 위해 유지합니다. MyBatis 설정은 `MyBatisConfigurationProperties`를 통해 세션 팩토리에 전달합니다. 기본 프로파일은 `local`이고 dev/prod는 `spring.config.additional-location`에서 DB 설정을 읽습니다. 프로파일별 Logback 설정은 `backend/src/main/resources/config/logger/`에 둡니다.

프로파일별 로그 설정은 `backend/src/main/resources/config/logger/logback-{profile}.xml`에 두고 각 프로파일의 `logging.config`에서 classpath 경로로 참조합니다. 세부 로그 정책은 운영 요구에 따라 조정합니다.

신규 기능은 `appDataSource`, `appSqlSessionFactory`, `appSqlSessionTemplate`, `appTransactionManager`를 사용합니다. legacy foundation probe는 기존 `catalog`·`archive` 전용 DAO와 세션 구성으로 남아 있습니다. 데이터소스별로 `@Qualifier`를 명시하고, 여러 DB에 걸친 트랜잭션은 구성하지 않습니다. 상세 설계는 [ADR-010](../decisions/ADR-010-app-datasource.md)을 따릅니다.

## 설정 관리 원칙

local/test는 `app`과 별도 H2 파일을 사용하는 `audit` 데이터소스를 구성합니다. `DatabaseMigrationConfiguration`이 각 데이터소스를 해당 클래스패스 SQL 위치 목록에 연결합니다. local/test에서 활성화되며 최종 위치 목록이 빈 DB는 적용을 건너뜁니다. [설정 방법](../operations/database-migration.md) [ADR-018](../decisions/ADR-018-per-datasource-migrations.md)

Spring 초기화 전 필요한 런타임 설정은 클래스패스 `config/runtime/` 바로 아래의 주제별 UTF-8 `.properties` 파일과 중앙 로더로 관리합니다. `foundation.config`의 `RuntimeSettingsLoader`가 파일을 읽고 불변 `RuntimeSettings`를 `RuntimeConfig`의 정적 필드 초기화 시 생성해 정적 `settings()`로 제공합니다. `main()`의 초기화 호출은 사용하지 않습니다. 실제 설정 항목은 아직 없어 현재 설정 객체는 필드 없는 record입니다. 새 항목은 명시적 필드와 중앙 로더 매핑을 함께 추가합니다. 상세 설계는 [ADR-014](../decisions/ADR-014-static-runtime-settings.md)를 따릅니다.

백엔드 애플리케이션 설정은 Spring YAML을 우선 사용합니다. 프로젝트 설정에 환경 변수 사용을 금지합니다. YAML은 가능한 한 주제별로 분리해 `spring.config.import`로 조합하며 비밀값이 포함된 파일은 저장소 밖에서 관리합니다. [ADR-013](../decisions/ADR-013-topic-based-spring-yaml.md)을 따릅니다.

현재 배포 설정은 [배포 및 서버 설정 운영](../operations/deployment.md)을 따릅니다.

## 확장 규칙

- 모든 프로젝트 테이블은 [공통 스키마 생성 규칙](../db-schema/schema-rules.md)과 [ADR-028](../decisions/ADR-028-common-audit-columns.md)에 따라 생성일·수정일·생성자·수정자를 저장합니다. 시스템 작업자 ID는 문자열 `'0'`이며 일반 사용자 아이디로 예약합니다. 모델은 `OffsetDateTime`과 `String`을 사용하고 실제 SQL에서 수정 정보를 갱신합니다.

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

사용자 식별은 user_key(변경 불가 내부 키), user_id(로그인 아이디), username(실명)으로 분리합니다. 로그인은 userId 요청으로 계정을 찾고 JWT sub에는 user_key를 넣습니다. 서비스 권한 판정·역할 연결·생성자·수정자는 내부 키만 사용합니다. 헤더는 서버 응답의 로그인 아이디와 존재하는 실명을 표시하고 이전 username 전용 탭 세션은 제거합니다. [ADR-031](../decisions/ADR-031-user-identity-fields.md)을 따릅니다.

프런트는 유효한 탭 세션이 없으면 로그인 화면을 표시합니다. 로그인 토큰·만료 시각·아이디는 sessionStorage에 보관하고 헤더에 아이디와 로그아웃을 표시합니다. 로그인 후 기본 시작 화면과 React 코드로 구성한 데이터셋 > 데이터셋 생성 메뉴를 제공합니다. `/datasets/new`에서 POST /api/datasets로 저장하고 생성 결과를 표시합니다. 일반 링크로 이동하고 기존 내비게이션 조회 API·DB 메뉴 트리는 사용하지 않습니다.

데이터셋 생성은 서비스의 `@PreAuthorize`에서 활성 계정을 확인합니다. app 전용 DAO와 트랜잭션을 사용하며 서버가 데이터셋 UUID·내부 user_key 작업자·동일 생성/수정 타임스탬프를 기록합니다. 생성자와 소유권은 구분하며 소유·공유·조회 정책은 아직 미정입니다. [ADR-030](../decisions/ADR-030-dataset-creation-contract.md)의 작업자 기준은 ADR-031이 대체합니다.

- 인증 모듈은 Spring Security 무상태 필터 체인과 JWT access token을 사용합니다. 로그인은 아이디·BCrypt 비밀번호 해시를 확인하고 HS256 JWT를 발급합니다.
- 계정 조회는 `app` 데이터소스의 `auth_user` 테이블을 사용합니다. local/test는 YAML의 비운영 전용 키를 쓰고 dev/prod는 외부 YAML의 `app.auth.jwt.secret`에서 읽습니다. 기본 유효기간은 15분입니다.
- JWT 필터가 인증을 등록하고 `@EnableMethodSecurity`로 활성화한 서비스 메서드의 `@PreAuthorize`가 필요한 권한을 확인합니다. 컨트롤러 정책 어노테이션·MVC 인가 인터셉터는 제거했습니다. URL은 필터 체인에서 통과시키며 선언 없는 서비스나 미등록 경로를 정책 누락으로 일괄 거부하지 않습니다. 상세 HTTP 계약은 [API 계약](../api/api.md)을 참고합니다.
- `AuthorizationService`는 실제 사용자 DAO와 `MyBatisEffectivePermissionDao`를 통해 계정 상태와 명시 권한을 확인합니다. 서비스의 `@PreAuthorize`에서 `hasPermission`을 호출해 현재 SecurityContext의 인증 주체·활성 계정·명시 권한을 확인합니다. 공통 스키마는 역할·사용자 권한 할당을 생성하지 않습니다. local 프로파일만 별도 app seed로 admin 계정·admin 역할·모든 기존 권한 연결을 준비하며 UI 전용 권한은 app 공통 seed에서 제거합니다. [ADR-022](../decisions/ADR-022-local-app-seed.md)를 따릅니다. 정책과 후속 작업은 [권한 설계 제안](../plans/authorization-design-proposal.md)을 참고합니다.

## DB 테이블 정의

DB 테이블은 `doc/db-schema/tables/` 아래 업무 그룹별 정의서로 관리합니다. 현재 테이블 정의는 [정의서 색인](../db-schema/tables/README.md)을 참고합니다.

## 화면·메뉴

기존 ui 모듈의 API·서비스·DAO·도메인·매퍼를 제거했습니다. V3 이력은 유지하고 V4에서 UI 테이블을 삭제합니다. UI 전용 권한은 공통 seed에서 정리하며 local seed는 관리자 계정·역할·현재 권한 연결만 유지합니다. [ADR-027](../decisions/ADR-027-remove-ui-module.md)을 따릅니다.
