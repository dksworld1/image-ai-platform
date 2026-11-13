# 아키텍처

## 이미지 설명 편집 — 2026-11-13

[ADR-042](../decisions/ADR-042-image-descriptions.md)에 따라 이미지 설명은 버전 행과 이벤트 이력에 저장합니다. 업로드 모달은 파일마다 description을 전송하고, 목록은 원본·임시 편집·선택을 분리해 선택된 실제 변경만 단일 PATCH로 보냅니다. DatasetImageService는 image:update와 대상 범위를 검사하고 데이터셋 행 잠금 안에서 설명·수정 감사 값·UPDATE 스냅샷을 한 트랜잭션으로 반영합니다. 한 행 실패 시 전부 롤백하며 새 파일 버전은 만들지 않습니다. 컨트롤러의 DTO 검증과 서비스 정규화 외에 별도 편집 프레임워크는 추가하지 않았습니다.

2026-11-13 이미지 최초 등록일시: 이미지 조회 SQL은 보존된 이전 버전 연결을 따라 버전 1의 created_at을 firstRegisteredAt으로 계산합니다. 같은 파일명 전체의 최솟값을 사용하지 않으며 DB 컬럼·별도 요청은 추가하지 않습니다. 응답의 현재 버전 createdAt과 구분하고 최초 시각의 상세 계약은 [API](../api/api.md#get-apidatasetsdatasetidimages)에 둡니다.

## 이미지 선택 삭제 — 2026-11-13

[ADR-041](../decisions/ADR-041-image-logical-deletion.md)에 따라 이미지 서비스에 delete 권한·대상 조건을 검사하는 단건 논리 삭제를 추가했습니다. 기존 데이터셋 행 잠금·삭제 표시 DAO·DELETE 이력을 재사용하며 파일을 제거하지 않습니다. 컨트롤러는 204/404 HTTP 경계만 처리합니다. 프런트는 기존 목록 그리드의 선택·확인·순차 삭제·부분 실패 표시를 적용합니다. 아래 이미지 delete API 미구현 설명은 이전 단계 기록입니다.

## 이미지 기능 권한 분리 — 2026-11-13

[ADR-040](../decisions/ADR-040-image-permissions.md)에 따라 DatasetImageService의 @PreAuthorize는 이미지 read·upload·update와 이미지 대상 조건을 AND로 검사합니다. 데이터셋 상세 서비스 의존성을 제거했고 대상 SQL은 이미지 매퍼에서 독립 관리합니다. 이미지 관리의 데이터셋 선택도 이미지 read 전용 target API를 사용합니다. 따라서 이미지 read만 부여받은 활성 멤버가 데이터셋 read 없이 이미지 목록·원본·썸네일을 사용할 수 있습니다.

현재 이미지 쓰기의 대상 조건은 활성 멤버이면서 공간 운영자 또는 데이터셋 생성자입니다. canUpload·canUpdate를 별도 진입점으로 두고 실제 쓰기 SQL에서도 조건을 확인합니다. 추후 일반 사용자의 이미지 소유·상태 등에 따른 조건을 추가할 때는 사전 판정과 쓰기 SQL을 함께 변경합니다. 별도 정책 엔진·테이블은 만들지 않았습니다. 이미지 delete는 카탈로그만 등록하고 개별 삭제 API는 아직 없습니다. 데이터셋 삭제의 잠금·조건은 데이터셋 DAO에서 관리하며 하위 이미지 이력·정리에 이미지 기능 권한을 추가 요구하지 않습니다.

## 이미지 버전·작업 이력·원본 확인 — 2026-11-13

[ADR-039](../decisions/ADR-039-image-version-history.md)에 따라 일반/수정 업로드는 같은 저장 루틴을 사용하되 명시적인 replaceExisting=true만 기존 활성 원본명을 교체합니다. 업로드와 데이터셋 삭제가 같은 데이터셋 행 잠금을 사용하여 현재 버전 판정·삭제 표시·새 버전 추가를 직렬화합니다. DB의 활성 이름 UNIQUE는 중복 현재 행을 막습니다. 이미지 DAO가 버전 메타데이터와 별도 이력 스냅샷을 같은 app 트랜잭션으로 저장하며 HTTP 규칙은 컨트롤러에 둡니다.

목록·개수·썸네일·원본 조회는 삭제 표시 버전을 제외합니다. 등록자·수정자는 user_key로 현재 로그인 ID·실명을 LEFT JOIN합니다. 버전 교체 시 이전 파일·행을 보존합니다. 데이터셋 삭제는 기존 FK CASCADE를 유지하면서 DatasetService가 활성 이미지 DELETE 이력을 먼저 남깁니다. 이력에는 부모 FK가 없어 삭제 이후에도 스냅샷을 보존합니다. 일반/수정 업로드 권한은 후속 ADR-040에서 upload·update로 분리했습니다.

프런트는 같은 업로드 모달에 수정 모드 제목·경고만 추가합니다. 썸네일 또는 원본명을 클릭하면 원본 확인 모달을 열고 인증된 content API 응답을 표시합니다. 모달 종료 시 요청을 취소하고 object URL을 해제합니다. 이력 탐색·복구·개별 이미지 삭제 UI는 추가하지 않았습니다. 아래 원본 조회 미구현·일반 업로드 전용 설명은 이전 단계 기록입니다.

## 데이터셋 이미지 저장 — 2026-11-12

dataset 모듈의 DatasetImageController는 multipart HTTP 경계만 처리하고 DatasetImageService가 권한·원본명 중복·콘텐츠 검증·파일 저장과 DB 등록을 담당합니다. MyBatis DAO는 app V18의 dataset_image를 사용합니다. 로컬 파일의 UUID 상대 경로와 사용자 표시용 원본명을 분리하며 저장·업로드 일반 설정은 application.yaml에 직접 작성합니다. 실제 INSERT에도 데이터셋 관리 범위를 확인하고 DB 실패 시 파일을 정리합니다. 목록은 기존 데이터셋 읽기 범위를 재사용합니다. [ADR-036](../decisions/ADR-036-dataset-image-upload.md), [API](../api/api.md#데이터셋-이미지-업로드), [테이블](../db-schema/tables/dataset/dataset-image.md)을 따릅니다.

2026-11-13: [ADR-038](../decisions/ADR-038-image-thumbnails.md)에 따라 업로드 서비스에서 이미지를 한 번 디코딩하고 PNG 썸네일을 동기 생성합니다. 원본·썸네일은 같은 루트에 별도 저장하고 V19의 thumbnail_path로 연결합니다. 실패 롤백 시 두 파일을 정리합니다. 썸네일 조회는 기존 데이터셋 읽기 권한·공간 범위를 재사용합니다.

이미지 업로드 UI는 한 차례 취소 후 2026-11-13 사용자 후속 요청으로 재개했습니다. 이미지 목록 독립 페이지·인증된 썸네일·데이터셋 행 우클릭 진입과 업로드 모달을 제공합니다. 프런트는 여러 파일을 기존 단건 API에 순차 전송하고 결과를 모달에 표시합니다. 백엔드 단건 업로드·메타데이터 목록·썸네일 조회를 제공하며 원본 다운로드·파일 삭제·배치·외부 저장소 추상화는 추가하지 않았습니다. 데이터셋 삭제는 DB 이미지 메타데이터를 함께 제거하지만 실제 파일 정리는 후속입니다.

## 데이터셋 삭제 경계 — 2026-11-11

DELETE API는 dataset 서비스의 `@PreAuthorize`에서 dataset:item:delete와 실제 공간 범위를 AND로 검사합니다. 활성 공간·ACTIVE 멤버십을 필수로 하고 OWNER·공간 ADMIN·데이터셋 생성자 중 하나를 허용합니다. MyBatis의 공통 SQL 조건을 사전 판정과 조건부 DELETE에 함께 적용합니다. 새 권한 검사 계층·UI·이미지 처리·배치는 추가하지 않았습니다. [ADR-035](../decisions/ADR-035-dataset-deletion-authorization.md)와 [API](../api/api.md#데이터셋-삭제)가 현재 기준입니다. 아래 삭제 미구현 설명은 이전 단계 이력입니다.

## 현재: 공간별 독립 데이터셋 — 2026-11-11

[ADR-034](../decisions/ADR-034-independent-dataset-creation.md)에 따라 다중 선택은 공간별 별도 ID의 독립 데이터셋 생성입니다. dataset.workspace_id 단일 FK와 한 트랜잭션의 다건 INSERT를 사용하고 다대다 연결·목록 그룹화는 제거했습니다. 목록은 활성 멤버십·공간과 단일 FK를 결합합니다. 생성 응답은 각 workspaceId의 데이터셋 배열이며 목록은 workspaceId·workspaceName을 반환합니다. local 미귀속 과거 데이터만 V17에서 첫 번째 공통 공간으로 배정합니다. 아래 다중 공간 공유 구조는 이전 이력입니다.

## 데이터셋 목록·다중 공간 생성 — 2026-11-11

`workspace` 모듈은 현재 사용자의 활성 가입 공간 조회를 제공하고 `dataset` 모듈은 기능 권한을 명시한 서비스에서 목록·생성을 처리합니다. 생성의 공간 범위 검사는 workspace 서비스의 `isMemberOfAll`을 재사용하며 다른 모듈 DAO를 직접 호출하지 않습니다. 저장은 dataset·dataset_workspace를 하나의 app 트랜잭션으로 수행합니다. 목록 SQL은 활성 공간·멤버십을 결합하고 서비스가 동일 dataset_id의 행을 묶어 접근 가능한 공간 정보만 반환합니다. 계약과 이관은 [ADR-033](../decisions/ADR-033-dataset-multiple-workspaces.md), [API](../api/api.md#데이터셋-목록)를 따릅니다. 아래 단일 공간·생성 전용 설명은 이전 단계 이력입니다.

프런트는 `/datasets` 목록과 `/datasets/new` 생성 페이지를 제공합니다. 생성의 native details 드롭다운에 체크박스를 두어 여러 공간을 선택하며, 서버가 모든 선택 공간의 멤버십을 재확인합니다. 공간 관리 화면과 데이터셋 상세·수정·삭제는 아직 없습니다.

## local H2 콘솔 — 2026-11-11

`spring-boot-h2console` 모듈과 기존 local YAML로 `/h2-console`을 제공합니다. local 전용 우선 보안 체인은 `PathRequest.toH2Console()`에만 적용하고 SAMEORIGIN 프레임을 허용합니다. 일반 API 보안 체인과 서비스 권한 검사는 유지합니다. 접속·검증 내역은 [운영 문서](../operations/deployment.md#local-h2-웹-콘솔--2026-11-11)를 따릅니다.

## 워크스페이스 스키마 기반 — 2026-11-11

app V10에 workspace·workspace_member와 dataset의 전환용 nullable workspace_id FK를 추가했습니다. local seed는 admin을 소유자·ACTIVE ADMIN 멤버로 하는 공통 워크스페이스를 준비합니다. 정의와 적용 범위는 [테이블 문서](../db-schema/tables/workspace/README.md)와 [ADR-032](../decisions/ADR-032-workspace-data-boundary.md)를 따릅니다. 실제 DB 적용·관리 화면·가입 승인·데이터셋 공간별 접근 검사는 아직 수행·구현하지 않았습니다. 기존 생성 API는 계속 공간 없이 저장하므로 공간 격리가 이미 적용된 상태가 아닙니다.

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

백엔드의 DB 외 일반 설정은 application.yaml에 직접 작성하고 프로파일 차이만 application-{profile}.yaml에서 재정의합니다. DB 접속 설정은 기존 YAML import를 유지합니다. 프로젝트 설정에 환경 변수 사용을 금지하며 비밀값은 저장소 밖의 YAML을 additional-location으로 읽습니다. [ADR-037](../decisions/ADR-037-application-yaml-settings.md)이 ADR-013의 일반 설정 주제별 import 방침을 대체합니다.

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

## 데이터셋 관리의 파일 유형 분리 — 2026-11-13

폴더 업로드(ADR-046)는 기존 단건 API를 사용하며 선택 multipart relativePath를 공통 도메인 UploadFilename에서 검증·정규화해 original_filename에 저장합니다. 폴더 엔티티나 물리 폴더 계층은 추가하지 않습니다. 유형별 서비스는 기존 권한·내용 처리·버전 관리 경계를 유지합니다.

현재 페이지는 DatasetManagePage이며 /datasets/manage에서 이미지 파일·데이터 파일 탭을 제공합니다. 기존 /datasets/images도 같은 페이지로 연결합니다. DatasetUploadModal과 목록 동작을 함께 사용하고 api/datasetEntries.ts에서 두 API의 imageId/fileId를 화면용 entryId로 변환합니다. 이미지 전용 썸네일·상세 조회와 데이터 파일 다운로드는 각각 유지합니다. 별도 공통 그리드 프레임워크나 의존성은 추가하지 않았습니다.

데이터 파일은 DatasetFileController → DatasetFileService → DatasetFileDao/MyBatisDatasetFileDao와 별도 mapper·dataset_file/history로 관리합니다. 이미지 모델·저장소·권한을 합치지 않습니다. 데이터 파일은 원본 바이너리 보관과 메타데이터 수명 주기를 담당하고 내용 파싱·분석 파이프라인은 담당하지 않습니다. 권한과 HTTP 상세는 [API](../api/api.md#데이터-파일-api--2026-11-13), 채택 근거는 [ADR-043](../decisions/ADR-043-dataset-file-tabs.md)을 따릅니다.

후속 [ADR-044](../decisions/ADR-044-data-file-binary-upload.md)에 따라 데이터 파일은 이미지도 허용하며 확장자·내용을 판별하지 않습니다. 데이터 파일 경로에서는 이미지 처리·썸네일 생성을 수행하지 않습니다.
