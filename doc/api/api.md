# API 계약

## 권한 검사 경계 변경 — 2026-11-09 확정

제한이 필요한 서비스 메서드에만 `@PreAuthorize`를 적용하며 모든 서비스에 강제하지 않습니다. 컨트롤러 퍼미션 체크는 완전히 제거하고 앞으로도 추가하지 않습니다. 서비스는 단일 계층으로 유지하며 여러 서비스 조합은 필요한 경우 업무별 상위 서비스에서 호출합니다. 서비스 Method Security 전환을 구현했으며 기존 관리 API의 필요 권한과 401·403·500 오류 계약은 유지합니다.

[ADR-026](../decisions/ADR-026-service-permission-boundary.md)에 따라 서비스 권한 검사를 구현했습니다. 이전 컨트롤러 권한 선언 의무·선언 누락 기본 거부·서비스 경계 보류 방침은 대체되었습니다. 비동기 인증 전달은 별도 보류 상태입니다.

상태: 초기 계약  
갱신일: 2026-11-08
기본 URL: `http://localhost:8080`

기존 메뉴·화면 API는 제거했습니다. [철거 안내](../archive/ui/api.md)를 참고하세요.

## 헬스체크

### `GET /api/health`

API 프로세스가 요청을 처리할 수 있는지 확인합니다.

성공 응답 `200 OK`:

```json
{ "status": "UP" }
```

이 엔드포인트는 인프라 및 외부 의존성의 상태까지 보장하지 않습니다.

## 인증

### `POST /api/auth/token`

아이디와 비밀번호를 검증하고 서명된 JWT access token을 발급합니다. 비밀번호는 서버에 저장된 BCrypt 해시와 비교합니다.

요청:

```json
{ "userId": "hong01", "password": "user-password" }
```

성공 응답 `200 OK`:

```json
{
  "accessToken": "<JWT>", "tokenType": "Bearer", "expiresIn": 900,
  "userKey": "7c148350-7fe1-43b4-b78e-40ce34cbaf49",
  "userId": "hong01", "username": "홍길동"
}
```

존재하지 않거나 비활성화된 계정, 잘못된 비밀번호는 동일한 `401 Unauthorized` 응답을 반환합니다. 보호된 API에는 `Authorization: Bearer <JWT>` 헤더를 전달합니다. 토큰은 기본 15분 후 만료되며 refresh token은 발급하지 않습니다. 계정 생성·비밀번호 변경 API는 제공하지 않습니다.

userId는 필수·최대 100자의 로그인 아이디이며 실명 username으로 로그인하지 않습니다. username은 실명 미상일 때 null입니다. JWT sub와 서버 인증 주체는 userKey이며 로그인 아이디·실명을 내부 키로 다시 해석하지 않습니다. 기존 username 로그인 요청은 userId 누락으로 400이며 프런트·백엔드를 함께 전환합니다. [ADR-031](../decisions/ADR-031-user-identity-fields.md)을 따릅니다.

`local`과 `test` 프로파일은 각 YAML의 비운영 전용 JWT 서명 키를 사용합니다. `dev`와 `prod`는 외부 YAML의 `app.auth.jwt.secret`로 UTF-8 기준 32바이트 이상의 키를 주입해야 합니다. 테이블 정의는 [인증 테이블 정의서](../db-schema/tables/auth/auth-user.md)에 있습니다.

## API 접근 정책

- JWT 필터는 전달된 Bearer 토큰을 검증하며 공개 API에 잘못된 토큰을 보내도 401을 반환합니다. 로그인·헬스에는 인증이 필수는 아닙니다.
- 컨트롤러에는 권한 정책을 선언하거나 검사하지 않습니다. `@PublicApi`·`@RequirePermission`과 MVC 인가 인터셉터는 제거했습니다.
- 접근 제한이 필요한 서비스 메서드만 `@PreAuthorize`를 선언합니다. 기존 UI 관리 API는 [ADR-027](../decisions/ADR-027-remove-ui-module.md)에 따라 제거했으며 `/api/ui/**`는 더 이상 제공하지 않습니다. 로그인·인증과 서비스 권한 판정 기반은 유지합니다.
- 선언 없는 서비스는 일괄 거부하지 않습니다. 미등록 경로는 404 등 Spring MVC의 기본 HTTP 처리를 따르며 컨트롤러 선언 누락에 대한 별도 403 처리는 없습니다. 요청 본문 검증 등 HTTP 처리는 서비스 호출 전에 실행될 수 있습니다.
- 정상 CORS preflight는 기존 Spring CORS 처리에서 종료됩니다. HEAD 등 실제 서비스 실행 요청은 동일한 서비스 권한 검사를 거칩니다. 리소스 소유권·공유 범위는 해당 업무 규칙에서 별도로 검사합니다.
- 비동기 재디스패치 자체가 서비스 권한을 재검사하지는 않습니다. 비동기 작업 실행 주체 전달은 별도 미구현 과제입니다.
## 변경 원칙

데이터셋 생성의 현재 계약은 아래 절을 따릅니다.

- 새 API는 메서드, 경로, 요청/응답 예시, 오류 응답, 인증 요구 여부를 이 문서에 기록합니다.
- 호환성에 영향을 주는 변경은 버전 정책과 전환 계획을 먼저 설계합니다.
- 구현의 실제 계약이 문서와 다르면 둘 중 하나를 변경해 일치시킵니다.

## 데이터셋 생성

갱신일: 2026-11-11
상태: 구현됨

### POST /api/datasets

`Authorization: Bearer <JWT>`와 `dataset:item:create`가 필요합니다. 서비스 `@PreAuthorize`에 기능 권한과 `workspaceService.isMemberOfAll(workspaceIds)`를 AND로 명시합니다. 선택한 모든 공간이 ACTIVE이고 현재 사용자가 각 공간의 ACTIVE 멤버여야 합니다. OWNER·ADMIN 역할만으로 기능 권한을 대신하지 않습니다.

요청:

```json
{ "name": "도로 표지판 이미지", "description": "표지판 분류 학습용 이미지", "workspaceIds": ["00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002"] }
```

name은 필수이며 공백만 있는 값을 허용하지 않고 공백 제거 전 최대 200자입니다. description은 선택이며 최대 2000자, 생략·null·빈 문자열·공백만 입력하면 null로 저장합니다. 두 값의 앞뒤 공백은 제거합니다. 이름 중복은 허용합니다.

workspaceIds는 하나 이상의 비어 있지 않은 공간 ID 배열이며 각 값은 최대 36자입니다. 중복은 제거합니다. 공간 하나라도 없거나 접근 불가하면 전체 요청을 403으로 거부하며 부분 저장하지 않습니다. 공간 없는 이전 클라이언트 요청은 400입니다. 선택 공간마다 다른 ID의 독립 데이터셋을 하나씩 만들고 한 app 트랜잭션으로 저장합니다. 같은 이름·설명으로 시작하지만 이후 변경은 공간별로 독립적입니다.

성공 응답: 201 Created, 생성된 데이터셋 배열. 중복 제거한 요청 공간 순서입니다.

배열 항목 예시:

```json
{
  "datasetId": "16ad971f-74a4-47ba-b451-2dd611e60220",
  "name": "도로 표지판 이미지",
  "description": "표지판 분류 학습용 이미지",
  "createdAt": "2026-11-10T12:00:00.123456Z",
  "updatedAt": "2026-11-10T12:00:00.123456Z",
  "createdBy": "7c148350-7fe1-43b4-b78e-40ce34cbaf49",
  "updatedBy": "7c148350-7fe1-43b4-b78e-40ce34cbaf49",
  "workspaceId": "00000000-0000-4000-8000-000000000001"
}
```

ID·생성일시·수정일시·작업자는 서버가 생성하며 요청에 같은 이름의 필드를 보내도 사용하지 않습니다. 생성일시에는 날짜·시간이 함께 포함됩니다. DB에는 마이크로초 정밀도의 시간대 포함 타임스탬프, 응답에는 ISO 8601 문자열을 사용합니다. 생성 시 두 일시와 두 작업자가 각각 같습니다. Location 헤더나 상세 URL을 생성 응답에 반환하지 않습니다.

위 객체는 응답 배열의 첫 항목 예시입니다. 위 요청은 해당 객체와 두 번째 공간의 별도 datasetId·workspaceId 객체를 담은 배열을 반환합니다. createdBy와 updatedBy는 인증된 사용자의 내부 userKey입니다. userId와 username은 기록하지 않으며 로그인 아이디·실명 변경에도 이 값은 유지됩니다.

| 오류 | 조건 |
|---|---|
| 400 | 누락·공백 이름, 길이 초과, 빈·누락·잘못된 workspaceIds, 잘못된 JSON·요청 본문 |
| 401 | 인증 없음·토큰 오류·만료, 계정 없음·비활성 |
| 403 | 생성 기능 권한 없음 또는 선택 공간 중 하나 이상에 활성 멤버십 없음 |
| 500 | DB 저장 등 서버 오류 |

요청 본문 검증은 서비스 인증 판정보다 먼저 실행될 수 있습니다. 클라이언트는 서버 오류 본문을 직접 표시하지 않고 상태에 따라 안내합니다. 생성 요청의 자동 재시도는 하지 않습니다. 수정 API는 아직 제공하지 않습니다. 저장 구조는 [dataset 테이블](../db-schema/tables/dataset/dataset.md), 현재 결정은 [ADR-034](../decisions/ADR-034-independent-dataset-creation.md)를 참고합니다.

## 데이터셋 삭제

### DELETE /api/datasets/{datasetId}

요청 본문 없이 활성 계정 JWT와 `dataset:item:delete` 권한으로 호출합니다. 서버가 대상 데이터셋의 실제 workspace_id를 확인하므로 사용자 키·소유자·공간 ID를 요청에서 받아 신뢰하지 않습니다.

아래 조건을 모두 충족해야 합니다.

- 공간이 ACTIVE이며 현재 사용자가 그 공간의 ACTIVE 멤버.
- 해당 공간의 OWNER 또는 ADMIN이거나, dataset.created_by가 현재 사용자의 내부 user_key와 일치.
- 공통 `dataset:item:delete` 기능 권한 보유. 운영자도 생략 불가.

성공은 `204 No Content`이며 dataset DB 행을 물리 삭제합니다. 독립 생성된 다른 공간의 데이터셋은 유지합니다. 목록 선택 삭제 UI는 이 API를 항목별 호출하고 부분 실패를 안내합니다. 이미지 업로드 도입 이후 dataset_image 메타데이터는 FK CASCADE로 함께 삭제합니다. 실제 이미지 파일 삭제·배치는 아직 없습니다.

| 응답 | 조건 |
|---|---|
| 204 | 허용된 대상 1건 삭제, 응답 본문 없음 |
| 401 | 인증 없음·토큰 오류·만료·계정 없음·비활성 |
| 403 | 기능 권한 없음, 비멤버·비활성 공간/멤버십, 일반 사용자의 다른 생성자 데이터, 미귀속 또는 없는 대상, 조건 변경 후 삭제 0건 |
| 500 | DB 등 서버 오류 |

서비스 `@PreAuthorize`에 기능 권한과 `datasetService.canDelete(datasetId)`를 AND로 선언합니다. 실제 DELETE에도 동일한 공간·멤버·운영자/생성자 조건을 적용하며 실패를 성공으로 처리하지 않습니다. 삭제 API의 자동 재시도는 하지 않습니다. 반복 삭제는 대상이 없어 403이며 204로 간주하지 않습니다. [ADR-035](../decisions/ADR-035-dataset-deletion-authorization.md)를 따릅니다.

## 데이터셋 목록

### GET /api/datasets

활성 계정 JWT와 `dataset:item:read` 권한이 필요합니다. 사용자 키·워크스페이스 선택 쿼리 없이 서버 인증 컨텍스트의 내부 user_key로 조회합니다. ACTIVE 멤버인 ACTIVE 공간에 속한 데이터셋만 반환합니다. 생성자는 조회 범위에 영향을 주지 않습니다. 공간별 독립 데이터셋은 각각 한 행이며 귀속 workspaceId·workspaceName을 반환합니다.

200 응답은 아래 항목들의 배열이며 빈 결과는 `[]`입니다. 최신 createdAt 역순, 동률 datasetId 오름차순입니다. 검색·페이지·필터는 현재 제공하지 않습니다.

```json
[{
  "datasetId": "16ad971f-74a4-47ba-b451-2dd611e60220",
  "name": "도로 표지판 이미지",
  "description": null,
  "createdAt": "2026-11-11T12:00:00.123456Z",
  "updatedAt": "2026-11-11T12:00:00.123456Z",
  "workspaceId": "00000000-0000-4000-8000-000000000001",
  "workspaceName": "공통 워크스페이스",
  "creatorUserId": "hong01",
  "creatorUsername": "홍길동",
  "imageCount": 12
}]
```

미귀속 데이터·승인 대기·탈퇴·제거·비활성 공간은 제외합니다. 인증 오류는 401, 조회 기능 권한 없음은 403, 조회 장애는 500입니다. 멤버십은 요청 시 DB에서 확인하며 JWT에 고정하지 않습니다.

생성자 정보는 dataset.created_by 내부 키를 auth_user.user_key에 LEFT JOIN하여 현재 user_id·username을 반환합니다. 생성 당시 이름의 스냅샷이 아닙니다. creatorUserId는 로그인 ID, creatorUsername은 실명입니다. 실명 미등록이면 creatorUsername이 null이며 계정이 없으면 두 값 모두 null입니다. 생성자 계정이 없어도 데이터셋은 조회 결과에 남습니다. 그리드는 null을 `-`로 표시합니다.

2026-11-13: imageCount는 해당 dataset_id에서 is_deleted=false인 dataset_image 메타데이터 행 수이며 없으면 0입니다. 교체된 이전 버전과 썸네일 파일은 별도로 세지 않으며 썸네일 미생성 과거 활성 이미지도 포함합니다. 실제 디스크를 스캔하지 않습니다. 목록 조회 SQL 안에서 집계하므로 데이터셋마다 별도 HTTP·DAO 요청을 추가하지 않습니다. 목록 표는 워크스페이스 다음에 「이미지 개수」를 표시합니다.

## 데이터셋 상세

### GET /api/datasets/{datasetId}

2026-11-12 구현. 활성 계정 JWT와 `dataset:item:read` 권한이 필요합니다. 서비스에서 기능 권한을 검사하고 조회 SQL에서 대상의 실제 workspace_id와 인증 컨텍스트의 user_key로 ACTIVE 공간의 ACTIVE 멤버인지 확인합니다. 생성자·관리자도 멤버 조건을 생략하지 않습니다. 클라이언트가 보낸 사용자·공간 정보는 접근 판정에 사용하지 않습니다.

성공은 `200 OK`이며 위 목록 항목과 동일한 10개 필드(imageCount 포함)의 단일 객체를 반환합니다. 이미지 목록은 포함하지 않습니다. 인증 없음·오류·만료·비활성 계정은 401, 기능 권한 없음·비멤버·비활성 공간·미귀속·존재하지 않는 대상은 모두 403입니다. 조회 장애는 500입니다. 대상 존재 여부를 별도로 공개하지 않습니다.

프런트는 목록의 이름 버튼에 포인터를 표시하고 클릭할 때 이 API로 새로 조회하여 상세 모달을 엽니다. 로딩·오류·수동 재시도를 제공하고 닫기 또는 Escape로 닫습니다. 닫을 때 진행 중 조회를 취소합니다.

## 데이터셋 이미지 업로드

### 이미지 기능 권한

2026-11-13 [ADR-040](../decisions/ADR-040-image-permissions.md)에 따라 데이터셋 기능 권한과 독립적으로 관리합니다.

| 기능 | 기능 권한 | 대상 조건 |
|---|---|---|
| 관리 대상 목록·이미지 목록·원본·썸네일 | dataset:image:read | 활성 공간의 ACTIVE 멤버 |
| 일반 신규 업로드 | dataset:image:upload | 활성 멤버이며 공간 OWNER·ADMIN 또는 데이터셋 생성자 |
| 수정 업로드 | dataset:image:update | 위와 동일. 이름 미일치로 신규 등록 시 upload도 필요 |
| 개별 이미지 삭제 | dataset:image:delete | 활성 멤버이며 공간 OWNER·ADMIN 또는 데이터셋 생성자 |

운영자도 기능 권한을 가져야 합니다. 이미지 조회에서 dataset:item:read를 요구하거나 dataset 상세 서비스를 호출하지 않습니다. 데이터셋 삭제에 따른 하위 이미지 처리는 dataset:item:delete로 승인된 작업이며 이미지 delete를 추가 요구하지 않습니다.

### GET /api/datasets/image-targets

이미지 관리의 데이터셋 선택 목록입니다. 활성 계정 JWT와 dataset:image:read로 현재 가입한 활성 공간의 데이터셋을 조회합니다. `200 [{"datasetId":"...","name":"...","workspaceId":"...","workspaceName":"..."}]`, 최신 생성순·동률 ID 오름차순이며 없으면 `[]`입니다. 데이터셋 설명·생성자·이미지 개수는 이 응답에 포함하지 않습니다. 인증 오류 401, 이미지 읽기 권한 없음 403, 조회 장애 500입니다. 일반 데이터셋 목록 GET /api/datasets의 dataset:item:read 계약은 그대로입니다.

### POST /api/datasets/{datasetId}/images

선택 쿼리 `replaceExisting`는 Boolean, 기본 false입니다. true는 명시적인 **이미지 수정 업로드**이며 dataset:image:update 권한과 위 쓰기 대상 조건이 필요합니다. 동일 데이터셋·원본명의 활성 이미지가 있으면 이전 행을 삭제 표시하고 새 imageId로 교체하여 versionCount를 +1 합니다. 이전 원본·썸네일과 DB 행은 지우지 않습니다. 없으면 upload 권한도 검사하여 새 파일·버전 1로 등록합니다. 성공은 양쪽 모두 201입니다. 기존 false의 중복 409 동작은 유지됩니다. 수정 모드는 반복 요청 시 버전이 추가되므로 자동 재시도하지 않습니다. [버전·이력 결정](../decisions/ADR-039-image-version-history.md)을 따릅니다.

`multipart/form-data`의 `file` 필드로 이미지 한 개를 보냅니다. 일반 모드는 dataset:image:upload, 수정 모드는 dataset:image:update를 서비스 @PreAuthorize에 명시하며 이미지 대상 판정을 AND로 검사합니다. 활성 공간·ACTIVE 멤버십과 공간 OWNER·ADMIN 또는 dataset.created_by 일치 조건을 적용하고 실제 이미지 INSERT·삭제 표시 SQL에서도 범위를 확인합니다. 데이터셋 읽기·수정·삭제 기능 권한을 재사용하지 않습니다.

선택 텍스트 필드 description은 파일별 설명이며 최대 2,000자입니다. 생략·빈 값·공백만 있는 값은 NULL로 저장하고 그 외 값은 원문을 보존합니다. 수정 업로드도 새 버전에 입력 설명을 저장하며 비워 두면 새 버전의 설명은 NULL입니다. 이전 버전의 설명은 보존됩니다. 길이 초과는 400이며 해당 업로드를 저장하지 않습니다.

이미지는 로컬 파일, 원본명·저장 상대 경로 등 메타데이터는 DB에 저장합니다. 기본 파일당 제한은 10MB이며 YAML에서 변경할 수 있습니다. 실제 콘텐츠를 판독한 PNG·JPEG·GIF·BMP와 첫 이미지 4천만 화소 이하를 허용합니다. 클라이언트 MIME·확장자는 검증 근거가 아닙니다. 클라이언트 경로를 제외한 원본 파일명은 공백을 임의로 제거하지 않고 최대 255자로 보존합니다. 서버 UUID·형식 확장자를 실제 저장 이름으로 사용합니다.

같은 데이터셋의 원본명이 같으면 409이며 대소문자는 구분합니다. 다른 데이터셋에는 동일 이름을 허용합니다. DB UNIQUE도 적용하므로 동시에 전송해도 중복 저장되지 않습니다. 각 요청은 독립 트랜잭션입니다. 2026-11-13 사용자 후속 요청으로 이미지 관리의 업로드 모달을 구현했으며 여러 파일을 단건 순차 전송해 파일별 결과를 표시합니다.

성공은 201 Created입니다.

```json
{
  "imageId": "f037b045-1051-43cc-92cc-974047965854",
  "datasetId": "16ad971f-74a4-47ba-b451-2dd611e60220",
  "originalFilename": "도로표지판.png",
  "description": "교차로 진입 방향의 표지판",
  "mediaType": "image/png",
  "sizeBytes": 51200,
  "createdAt": "2026-11-12T12:00:00.123456Z",
  "firstRegisteredAt": "2026-11-12T12:00:00.123456Z",
  "createdBy": "7c148350-7fe1-43b4-b78e-40ce34cbaf49",
  "updatedAt": "2026-11-12T12:00:00.123456Z",
  "updatedBy": "7c148350-7fe1-43b4-b78e-40ce34cbaf49",
  "versionCount": 1,
  "creatorUserId": "hong01",
  "creatorUsername": "홍길동",
  "updaterUserId": "hong01",
  "updaterUsername": "홍길동",
  "thumbnailUrl": "/api/datasets/16ad971f-74a4-47ba-b451-2dd611e60220/images/f037b045-1051-43cc-92cc-974047965854/thumbnail"
}
```

| 응답 | 조건 |
|---|---|
| 201 | 원본·썸네일·DB·이력 등록 성공 |
| 400 | 비어 있거나 과도한 파일명, 빈 파일, 유효하지 않은 이미지·형식·화소 또는 서비스 크기 제한 위반 |
| 401 | 미인증·토큰 오류·계정 없음·비활성 |
| 403 | 업로드 기능 권한 없음, 운영자/생성자 조건·활성 공간·멤버십 불충족, 없는 대상 |
| 409 | 같은 데이터셋에 동일 원본 파일명 존재. 안내 문구: “이미 존재하는 이미지입니다.” |
| 413 | HTTP multipart 파일/요청 크기 제한 초과 |
| 500 | 파일 저장·DB 장애 |

업로드 시 원본과 별도 PNG 썸네일을 함께 저장합니다. 긴 변 기본 256px, 비율 유지, 작은 이미지 확대 없음이며 GIF는 첫 프레임을 사용합니다. 파일·DB·이력 저장 실패 시 삭제 표시·새 버전·이력을 롤백하고 이번에 작성한 두 파일만 정리합니다. 기존 파일은 보존합니다. 정리 실패·프로세스 중단의 한계는 [저장 운영](../operations/deployment.md)에 기록합니다. 실제 파일 경로는 응답에 노출하지 않습니다. 자동 재시도는 없으며 응답을 받지 못하면 아래 목록으로 확인합니다.

### GET /api/datasets/{datasetId}/images

응답의 firstRegisteredAt은 previous_image_id 연결을 따라 찾은 같은 이미지 계열의 버전 1 created_at입니다. 첫 등록 시 createdAt과 같고 교체 후에도 유지됩니다. createdAt은 현재 버전 자체의 등록일시이며 목록에서 「현재 버전 등록일시」로 표시합니다. 파일명이 같더라도 삭제 후 새로 등록한 별도 계열이나 다른 데이터셋의 시각을 섞지 않습니다. 보존된 이전 버전 행에서 조회 시 계산하므로 신규 저장 컬럼·이관은 없습니다. 기존 연결이 손상되어 버전 1을 찾지 못하면 null이며 화면은 `-`로 표시합니다.

활성 계정 JWT와 dataset:image:read 및 대상 데이터셋의 활성 공간·ACTIVE 멤버십이 필요합니다. 데이터셋 상세 서비스의 dataset:item:read 검사를 경유하지 않습니다. 200은 위 메타데이터 객체의 배열이며 삭제 표시 버전을 제외하고 createdAt DESC·imageId ASC입니다. 없으면 `[]`, 인증 오류는 401, 기능 권한 또는 공간 접근 불가는 403, 장애는 500입니다. 일반 활성 멤버도 이미지 읽기 권한이 있으면 조회할 수 있습니다. 기존 썸네일 미생성 이미지의 thumbnailUrl은 null입니다. 등록자·수정자 ID/이름은 각 감사 user_key로 LEFT JOIN한 현재 로그인 ID·실명이며 미등록·삭제 계정은 null입니다. createdBy·updatedBy는 내부 키입니다. 새 버전 행의 생성 감사 정보는 해당 버전 업로더이며 최초 업로더는 보존된 이전 버전·이력에서 추적합니다.

### GET /api/datasets/{datasetId}/images/{imageId}/thumbnail

위 목록과 동일한 JWT·읽기 권한·데이터셋 접근 범위를 검사합니다. 성공은 `200 image/png`, `Cache-Control: no-store`이며 PNG 바이트를 반환합니다. URL만으로 공개 접근할 수 없으며 클라이언트는 Authorization Bearer 헤더를 포함해 요청해야 합니다. 인증 오류 401, 데이터셋 접근 불가 403, 접근 가능한 데이터셋 안의 이미지 없음·삭제 표시 버전·다른 데이터셋 이미지·썸네일 미생성·파일 없음 404, 파일 읽기 장애 500입니다.

### GET /api/datasets/{datasetId}/images/{imageId}/content

현재 활성 버전의 원본 바이트를 반환합니다. JWT·dataset:image:read·대상 공간 접근 범위는 썸네일과 동일하며 이전 삭제 표시 버전은 조회할 수 없습니다. 성공은 200, 검증된 원본 mediaType(PNG·JPEG·GIF·BMP)의 Content-Type과 `Cache-Control: no-store`입니다. 인증 오류 401, 기능 권한 또는 공간 접근 불가 403, 이미지 없음·다른 데이터셋 이미지·삭제 표시·파일 없음 404, 파일 읽기 장애 500입니다. 저장 경로·공개 URL을 제공하지 않습니다. 프런트는 인증된 응답을 object URL로 표시하고 상세 모달 종료 시 해제합니다.

생성·교체·이미지 삭제·데이터셋 삭제 이력은 같은 app 트랜잭션에서 dataset_image_history에 저장합니다. 데이터셋 삭제 시 현재 이미지 DELETE 스냅샷을 남긴 뒤 기존 FK CASCADE로 버전 행을 제거하며 이력·실제 파일은 유지합니다. 이력 조회 API는 아직 제공하지 않습니다.

### DELETE /api/datasets/{datasetId}/images/{imageId}

현재 활성 이미지 한 개를 삭제 표시합니다. 활성 계정 JWT와 dataset:image:delete, 대상 데이터셋의 활성 공간·ACTIVE 멤버십 및 공간 OWNER·ADMIN 또는 데이터셋 생성자 조건이 필요합니다. 서비스 @PreAuthorize에 기능 권한과 canDelete를 AND로 선언하며 실제 변경 SQL도 같은 대상 조건을 검사합니다. 이미지 read·upload·update나 데이터셋 기능 권한을 대신 사용하지 않습니다.

성공은 204 No Content입니다. DB 행·원본·썸네일은 보존하고 is_deleted=true 및 수정 시각·작업자만 변경합니다. 생성 감사 값과 버전 번호는 유지하며 같은 트랜잭션에 DELETE 이력을 저장합니다. 목록·개수·원본/썸네일 조회에서 제외됩니다. 삭제 후 같은 이름의 신규 업로드는 새 ID·버전 1입니다.

| 응답 | 조건 |
|---|---|
| 204 | 삭제 표시·이력 저장 성공 |
| 401 | 미인증·토큰 오류·계정 없음/비활성 |
| 403 | delete 권한 또는 대상 조건 불충족, 없는 데이터셋 |
| 404 | 접근 가능한 데이터셋 안의 이미지 없음·다른 데이터셋 이미지·이미 삭제되거나 교체된 ID |
| 500 | DB·이력 저장 장애. 트랜잭션 롤백 |

현재 imageId만 처리하므로 오래된 목록의 삭제 요청이 같은 원본명의 새 버전을 삭제하지 않습니다. 프런트 선택 삭제는 확인 후 단건 순차 호출하고 성공 행만 제거하며 실패 행·선택·사유를 보존합니다. 항목별 독립 트랜잭션이며 자동 재시도하지 않습니다. 통신 단절은 결과를 확정할 수 없어 목록 새로고침을 안내합니다. [ADR-041](../decisions/ADR-041-image-logical-deletion.md)을 따릅니다.

### PATCH /api/datasets/{datasetId}/images/descriptions

선택한 이미지 설명들을 하나의 트랜잭션으로 저장합니다. 활성 계정 JWT와 dataset:image:update, 대상 데이터셋의 활성 공간·ACTIVE 멤버십·공간 OWNER/ADMIN 또는 데이터셋 생성자 조건이 필요합니다. 서비스 @PreAuthorize와 실제 수정 SQL에서 범위를 확인합니다.

```json
{"items":[{"imageId":"f037b045-1051-43cc-92cc-974047965854","description":"변경한 설명"},{"imageId":"79eced1a-2aca-4ac3-8ab5-71b89276598d","description":null}]}
```

items는 필수·1건 이상이며 중복 imageId는 400입니다. imageId는 필수·최대 36자, description은 최대 2,000자입니다. null·생략·빈 값·공백만 있는 설명은 삭제를 뜻합니다. 현재 활성 이미지 ID만 처리하므로 삭제·교체된 행이나 다른 데이터셋 이미지가 있으면 404로 전체 롤백합니다. 성공 200은 요청 순서대로 저장한 이미지 메타데이터 배열이며 description·수정자 표시·updatedAt을 포함합니다.

변경 시 설명과 수정 감사 값만 갱신하고 같은 트랜잭션에 UPDATE 이력을 추가합니다. 버전 번호·생성 감사 값·원본·썸네일은 유지합니다. 401은 인증 오류, 403은 기능 권한/대상 조건 미충족, 400은 입력 오류, 404는 대상 이미지 없음, 500은 DB·이력 저장 장애입니다. 하나라도 실패하면 전부 롤백하며 부분 성공 응답은 없습니다. 통신 단절은 결과가 불명확하므로 자동 재시도하지 않습니다.

프런트는 선택된 실제 변경 행만 전송합니다. 저장 확인창에는 미선택된 변경 행 수와 성공 후 편집 내용 초기화만 안내합니다. 성공 시 미선택 편집은 원본으로 초기화하고 실패 시 편집·선택을 유지합니다. 선택된 변경 행이 0이면 확인 후 프런트 편집만 초기화하며 빈 PATCH를 보내지 않습니다. [ADR-042](../decisions/ADR-042-image-descriptions.md)를 따릅니다.

## 가입 워크스페이스 목록

### GET /api/workspaces

활성 계정 JWT가 필요하며 별도 기능 권한은 요구하지 않습니다. 현재 사용자가 ACTIVE 멤버인 ACTIVE 공간의 `[{"workspaceId":"...","name":"..."}]`를 이름·ID 오름차순으로 반환합니다. 없으면 `[]`입니다. 생성 화면에서 선택할 공간을 제공하며 전체 공간 탐색·관리 API가 아닙니다. 미인증·비활성 계정은 401, 조회 장애는 500입니다.

## 데이터 파일 API — 2026-11-13

현재 데이터셋 관리 화면은 이미지 파일과 데이터 파일 탭을 제공합니다. 이미지 API는 유지하며 데이터 파일은 다음의 독립 계약을 사용합니다.

| 메서드·경로 | 기능 | 기능 권한 |
|---|---|---|
| GET /api/datasets/file-targets | 접근 가능한 데이터셋·워크스페이스 선택 목록 | dataset:file:read |
| GET /api/datasets/{datasetId}/files | 활성 파일 목록 | dataset:file:read |
| POST /api/datasets/{datasetId}/files | multipart 단건 등록 | dataset:file:upload |
| POST /api/datasets/{datasetId}/files?replaceExisting=true | 같은 원본명 파일 교체 | dataset:file:update |
| PATCH /api/datasets/{datasetId}/files/descriptions | 설명 일괄 저장 | dataset:file:update |
| DELETE /api/datasets/{datasetId}/files/{fileId} | 삭제 표시, 성공 204 | dataset:file:delete |
| GET /api/datasets/{datasetId}/files/{fileId}/content | 원본 다운로드 | dataset:file:read |

모든 요청은 인증이 필요합니다(미인증 401). 조회는 활성 공간의 활성 멤버, 쓰기는 추가로 공간 OWNER/ADMIN 또는 데이터셋 생성자여야 합니다. 이미지·데이터셋 권한으로 대신 허용하지 않습니다. 수정 업로드에서 원본명이 없는 신규 파일을 만들려면 upload 권한도 필요합니다. 대상 조건·권한 불충족은 403, 접근 가능한 데이터셋에서 파일 부재·삭제·다른 데이터셋 파일은 404입니다.

POST는 file과 선택 description(최대 2,000자)을 받습니다. 정상 201이며 동일 데이터셋의 활성 원본명 중복은 409입니다. 다른 데이터셋의 같은 이름은 허용합니다. 원본명은 단일 파일명 또는 선택 폴더부터의 상대 경로로, 전체 1~1,024자·요소별 최대 255자이며 빈 파일·파일별 제한 초과·잘못된 설명은 400입니다. 서버 multipart 한도 초과는 파일별 서비스 검증 전에 거부될 수 있습니다. ADR-044에 따라 이미지도 허용하며 확장자·파일 시그니처로 유형을 판별하거나 거부하지 않습니다. 모든 파일을 원본 바이너리로 보관하고 썸네일은 생성·관리하지 않습니다. 이미지 탭과 데이터 파일 탭의 원본명 중복 검사는 서로 독립적입니다.

목록·등록 응답은 fileId, datasetId, originalFilename, description, mediaType, sizeBytes, createdAt, updatedAt, createdBy, updatedBy, versionCount, firstRegisteredAt, creatorUserId, creatorUsername, updaterUserId, updaterUsername입니다. 데이터 파일은 파싱하지 않는 원본 바이너리이며 mediaType은 application/octet-stream입니다. 콘텐츠는 이 형식과 UTF-8 원본명 Content-Disposition: attachment, Cache-Control: no-store로 반환합니다. 썸네일·인라인 미리보기는 제공하지 않습니다.

설명 PATCH 요청은 {"items":[{"fileId":"UUID","description":"설명 또는 null"}]}이며 변경된 파일 배열을 반환합니다. 빈 설명은 NULL, 선택 행 전체를 단일 트랜잭션으로 저장하며 하나라도 실패하면 이력까지 롤백합니다. 설명 변경은 버전을 올리지 않습니다.

수정 업로드는 기존 행에 삭제 표시하고 새 fileId·버전+1·previousFileId를 생성하며 새 설명은 업로드 입력을 사용합니다. 이전 파일과 이력은 보존합니다. 일반 삭제도 파일을 지우지 않으며 현재 목록·다운로드에서 제외합니다. 최초 등록 시각은 이전 버전 연결에서 계산합니다. 별도 이력 조회 API는 아직 없습니다. 데이터셋 삭제는 기존 dataset:item:delete 조건으로 하위 파일 DELETE 이력을 남기고 메타데이터를 FK CASCADE로 제거하되 물리 파일·이력은 보존합니다.

## 이미지·데이터 파일 폴더 업로드 — 2026-11-13

두 POST 업로드 API에 선택 multipart 필드 relativePath를 추가했습니다. 예: file=sample.png, relativePath=선택폴더/하위/sample.png. 응답·목록 originalFilename과 이력에 이 경로를 보존합니다. 일반·수정 업로드 모두 동일합니다. relativePath 생략 시 기존처럼 multipart 파일명에서 마지막 파일명만 저장합니다.

서버는 구분자를 /로 통일하며 전체 1,024자·각 요소 255자 제한, 빈 요소·절대 경로·드라이브 표기·. 또는 ..·제어 문자 금지를 적용합니다. 잘못된 경로는 400입니다. 동일 데이터셋·동일 유형 내 전체 경로를 기준으로 중복 409와 교체를 판정합니다. 선택폴더/a/image.png와 선택폴더/b/image.png는 별개입니다. 폴더 업로드는 기존 단건 API 순차 호출이며 파일별 성공·실패가 독립적입니다. 빈 폴더는 등록하지 않습니다.

개별 데이터 파일의 Content-Disposition 다운로드명은 전체 경로의 마지막 파일명입니다. 디스크 저장명은 원본 상대 경로와 분리해 기존 UUID 규칙을 사용합니다. 데이터 파일의 확장자도 경로의 마지막 파일명에서만 추출합니다. 권한·형식별 썸네일 정책은 유지합니다. [ADR-046](../decisions/ADR-046-folder-upload-original-paths.md)을 따릅니다.