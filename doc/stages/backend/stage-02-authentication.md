# 2단계: 인증과 권한

## 기존 UI 모듈 철거 — 2026-11-09

[ADR-027](../../decisions/ADR-027-remove-ui-module.md)에 따라 기존 화면·메뉴 API, 서비스·DAO·도메인·매퍼와 프런트 동적 내비게이션을 제거했습니다. 아래 기존 UI 구현·검증 내용은 과거 이력이며 현재 제공 기능이 아닙니다. 로그인·세션·로그아웃과 공통 레이아웃은 유지합니다. 새 메뉴 구현은 별도 작업이며 URL은 React 코드에서, 숨김은 메뉴 렌더링에서 관리합니다.

## 권한 검사 경계 변경 — 2026-11-09 확정

제한이 필요한 서비스 메서드에만 `@PreAuthorize`를 적용하며 모든 서비스에 강제하지 않습니다. 컨트롤러 퍼미션 체크는 완전히 제거하고 앞으로도 추가하지 않습니다. 서비스는 단일 계층으로 유지하며 여러 서비스 조합은 필요한 경우 업무별 상위 서비스에서 호출합니다. 서비스 Method Security 전환을 구현했으며 기존 관리 API의 필요 권한과 401·403·500 오류 계약은 유지합니다.

[ADR-026](../../decisions/ADR-026-service-permission-boundary.md)이 아래 기존 컨트롤러 권한 선언 의무·선언 누락 기본 거부·서비스 경계 보류 방침을 대체합니다. 아래 컨트롤러·MVC 인터셉터 설명과 검증 결과는 전환 전 구현 또는 당시 이력이며 새 구현 지침이 아닙니다. 서비스 권한 검사는 구현 완료이며 비동기 인증 전달은 별도 보류 상태입니다.

- 상태: 진행 중
- 선행 단계: 1단계
- 관련 문서: [제품 개요](../../product/project-overview.md), [아키텍처](../../architecture/architecture.md)
- 관련 결정: [ADR-007 백엔드 모듈 경계](../../decisions/ADR-007-backend-modules.md)
- 관련 결정: [ADR-009 아이디·비밀번호 로그인과 JWT](../../decisions/ADR-009-username-password-jwt.md)
- 권한 설계 제안: [역할 기반 API 권한과 판정 일관성](../../proposals/authorization-design-proposal.md) (검토 중)

## 목표

사용자와 요청 주체를 식별하고 이후 데이터셋 및 작업에 대한 접근 제어의 기반을 마련합니다. DB 기반 기능은 1단계에서 정한 데이터 소스와 트랜잭션 원칙을 따릅니다.

## 핵심 개념과 원리

- 인증은 요청 주체가 누구인지 확인하고, 인가는 그 주체가 특정 작업을 수행할 수 있는지 판단합니다.
- 모든 보호 대상 요청은 신뢰할 수 있는 인증 정보와 명시적인 권한 판정을 거쳐야 합니다.
- 데이터 소유권과 역할은 분리해서 모델링하고 최소 권한 원칙을 적용합니다.
- 비밀 정보와 자격 증명은 저장소에 기록하지 않으며, 로그에도 노출하지 않습니다.

## 설계 범위

인증 방식, 사용자 수명 주기, 역할·권한 모델, 세션 또는 토큰 정책, 비인증 요청 처리, 감사 기록과 테스트 경계를 정합니다.

인증·권한 기능은 `com.imageaiplatform.auth` 모듈이 소유합니다. HTTP 진입점, 업무 흐름, 저장소 계약·구현, 도메인 규칙은 모듈 내부의 `controller`, `service`, `dao`, `domain` 역할 패키지에 둡니다.

## 결정 기록

- 채택: Spring Security 기반 무상태 인증과 아이디·비밀번호 로그인
- 채택: 아이디·비밀번호 검증 후 HS256 JWT access token 발급, 기본 수명 15분
- 채택: 사용자 비밀번호는 BCrypt 해시로 저장하며 local/test JWT 서명 키는 YAML에, dev/prod 키는 외부 YAML의 `app.auth.jwt.secret`로 설정
- 미결정: 계정 가입·초대·복구와 계정 프로비저닝 절차
- 미결정: 역할 체계와 권한 모델
- 미결정: refresh token, 로그아웃·토큰 폐기와 키 회전 정책

## 완료 조건

- 인증과 인가의 경계 및 보호 대상이 문서화되어 있습니다.
- 허용·거부 사례와 계정 수명 주기가 정의되어 있습니다.
- 비밀 정보 취급 및 인증 실패 응답 원칙이 정의되어 있습니다.
- 구현 후 자동 검증 결과가 실제 실행 내용과 함께 기록되어 있습니다.

## 현재 구현

- 2026-11-08 메뉴·화면 지원: ui 모듈에서 기존 auth 서비스 경계로 유효 권한을 조회해 허용 화면·메뉴를 반환하고 관리 권한으로 CRUD를 제공합니다. app V3, 신규 요청 테스트 13개 포함 전체 58개 통과. 상세 계약은 [메뉴·화면 API](../../api/ui.md)를 참고하며 프런트 연동은 별도입니다.

- `POST /api/auth/token`에서 계정 자격 증명을 검증하고 JWT를 발급합니다. 보호 API는 Bearer 토큰을 확인하며 `/api/health`와 로그인 경로는 비인증으로 허용합니다.
- 계정은 `app` 데이터소스의 `auth_user` 테이블에서 찾습니다. 계정 생성 API는 제공하지 않으며 local seed로 개발용 admin 계정을 준비합니다.
- 테이블 구조는 [인증 테이블 정의서](../../db-schema/tables/auth/auth-user.md)에 기록했습니다.
- access token 발급 및 검증은 구현했습니다. 역할 관리·refresh token·폐기 및 감사 기능은 미구현입니다.
- 권한 판정 서비스와 도메인·DAO를 추가했습니다. 2026-11-06 권한 테이블의 실제 MyBatis 조회 DAO를 구현하고 서비스를 Spring 빈으로 연결했습니다. API 인가는 전역 MVC 인터셉터에 연결했고 기본 역할·권한 데이터는 생성하지 않습니다. 설계와 상세 진행 내용은 [권한 설계 제안](../../proposals/authorization-design-proposal.md)에서 관리합니다.
- 2026-11-04 검증: JDK 21·Maven 3.9.11에서 `mvn test` 성공. 신규 권한 판정 단위 테스트 7개와 기존 기반 테스트 4개, 총 11개가 모두 통과했습니다. 아래 이전 기록의 실행 실패와 구분합니다.
- 2026-11-06 검증: 신규 RBAC DB 통합 테스트 7개를 포함해 `mvn test` 전체 18개 통과. H2에서 제약·실제 권한 조회를 확인했습니다. API 인가·운영 DB 검증은 아직 수행하지 않았습니다.
- 2026-11-06 후속 API 인가 적용: `@PublicApi`·`@RequirePermission`과 전역 MVC 인터셉터를 연결했습니다. 신규 요청 테스트 9개 포함 전체 30개 통과. 위 18개 검증 시점과 달리 현재 API 인가 경로도 검증했으며, 운영 DB와 서비스 Method Security·작업 내부 비동기 전달은 다음 범위입니다.
- 검증: `git diff --check` — 성공. 이전 Maven 컴파일은 사용자 Maven 캐시의 `.m2/repository/.locks` 접근 거부로 의존성 해석 전에 중단됐습니다.

## 보안 준수 TODO

2026-11-07 기준, 서비스 경계·비동기 인가(권한 6단계)는 보류합니다. 업무 서비스에 해당 인가 요구가 생기면 [권한 설계 제안](../../proposals/authorization-design-proposal.md)의 단계표에 따라 재개합니다.

현재 로그인 발급 기능은 보안 규정 준수 검토를 마치지 않았습니다. 기능 개발은 계속 진행하고, 운영 배포 전에 아래 항목을 확인합니다.

- [ ] 적용 대상 규정, 시스템 범위와 필요한 인증 보증 수준을 확정합니다.
- [ ] 비밀번호 해시 알고리즘·비용·입력 길이, 비밀번호 설정 정책과 로그인 실패 횟수 제한을 대상 규정에 대조하고 보완합니다.
- [ ] JWT 서명 키 생성·보관·교체, 토큰 용도·issuer/audience 검증과 로그아웃/폐기 정책을 확정합니다.
- [ ] 운영 HTTPS, 비밀정보·인증정보 로그 처리, 클라이언트 토큰 보관과 MFA 요구 여부를 검토합니다.
- [ ] 보완 후 자동 검증 및 배포 환경 설정을 확인하고 준수 근거를 기록합니다.

## 로컬 관리자·메뉴 seed

- local 전용 db/seed/app/local의 반복 SQL로 admin 계정(BCrypt, 활성), 소문자 admin 역할과 현재 등록된 모든 권한 연결을 준비합니다. 시작·화면 관리·메뉴 관리 메타데이터는 app/common의 별도 SQL에서 등록합니다.
- 기존 V1~V3와 test·dev·prod의 기본 seed 정책은 변경하지 않습니다. React 관리 화면은 후속 작업이며 [ADR-022](../../decisions/ADR-022-local-app-seed.md)와 [마이그레이션 설정](../../operations/database-migration.md)을 따릅니다.

- 2026-11-08 DDL·DML 분리: V3의 권한 등록을 db/seed/app/common/R__01_ui_permissions.sql로 이관하고 공통·local 경로 및 검증을 갱신했습니다. 새 검증 DB에서 전체 62개 테스트 통과. 이미 V3 적용한 DB의 checksum 정리는 [ADR-023](../../decisions/ADR-023-ddl-dml-script-separation.md)을 따르며 실제 DB repair는 미실행입니다.

## 현재 검증 결과

- 2026-11-08: JDK 21.0.13·Maven 3.10.0에서 현재 백엔드 전체 59개 테스트 통과. 계정 등록용 전용 테스트 3개는 현재 범위에서 제외되었습니다.
- 실행 중인 로컬 서버 DB와 분리해 mvn test "-Ddatasource.app.url=jdbc:h2:mem:remove-registration-app;DB_CLOSE_DELAY=-1" "-Ddatasource.audit.url=jdbc:h2:mem:remove-registration-audit;DB_CLOSE_DELAY=-1"로 검증했습니다. 로그인·인가·메뉴 관리·공통 및 로컬 seed 검증은 유지합니다.

## 서비스 권한 검사 구현·검증 — 2026-11-09

- `@EnableMethodSecurity`를 활성화하고 UI 관리 메서드 6개에만 `@PreAuthorize`를 적용했습니다. 공개 조회는 기존 화면 정책을 유지합니다.
- 컨트롤러의 `@PublicApi`·`@RequirePermission`과 MVC 인가 인터셉터·등록 설정을 제거했습니다. 권한 확인 전용 계층이나 제품용 조합 서비스를 추가하지 않았습니다.
- 테스트의 별도 상위 서비스가 두 보호 서비스 빈을 주입받아 호출하도록 구성해 각 권한 누락 시 거부와 양쪽 권한 보유 시 성공을 검증했습니다. UI 서비스 직접 호출의 인증·권한 거부와 업무 미실행, 제한 없는 서비스 호출, HTTP 관리 API·HEAD·CORS와 권한 조회 DB 장애 시 500 응답·데이터 미변경도 검증했습니다.
- Java 21.0.13·Maven 3.10.0 확인 후 `mvn test "-Ddatasource.app.url=jdbc:h2:mem:service-permission-app;DB_CLOSE_DELAY=-1" "-Ddatasource.audit.url=jdbc:h2:mem:service-permission-audit;DB_CLOSE_DELAY=-1"` 실행: 전체 62개, 실패·오류 0개.
- 기본 파일형 테스트 DB에는 기존 V3 체크섬 충돌이 있어 최초 전체 실행은 실패했습니다. 이후 별도 메모리 DB로 검증했으며 기존 DB의 이력·데이터는 수정하지 않았습니다. 비동기 전달·민감 작업 동시성은 별도 과제입니다.
## UI 모듈 철거 검증 — 2026-11-09

- 프런트: `node --test frontend/test/*.test.cjs` 9개 통과, `npm --prefix frontend run build` 성공. 로그인·세션 복원·로그아웃·만료 처리는 유지하고 로그인 후 메뉴를 렌더링하지 않는 것을 확인했습니다.
- 백엔드: Java 21.0.13·Maven 3.10.0 확인 후 `mvn clean test`를 별도 메모리 DB에서 실행해 삭제 리소스를 정리했고, 최종 추가 검증을 포함한 `mvn test "-Ddatasource.app.url=jdbc:h2:mem:remove-ui-app;DB_CLOSE_DELAY=-1" "-Ddatasource.audit.url=jdbc:h2:mem:remove-ui-audit;DB_CLOSE_DELAY=-1"`에서 49개 통과, 실패·오류 0개.
- 신규 DB의 최종 UI 테이블 부재, 기존 V3 데이터의 V4 제거, UI 권한·역할 연결만 정리, 다른 권한 보존, 재적용 0건과 제거 API의 404 응답을 검증했습니다. 실제 로컬·운영 DB 마이그레이션이나 브라우저 검증은 실행하지 않았습니다.
