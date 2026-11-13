# `auth_permission` 테이블 정의

- 그룹: 인증·권한
- 데이터소스: `app`
- 상태: 권한 조회 DAO 구현
- 대응 모델: `com.imageaiplatform.auth.domain.AuthPermission`
- 조회 계약: `com.imageaiplatform.auth.dao.EffectivePermissionDao`
- 조회 구현: `com.imageaiplatform.auth.dao.impl.MyBatisEffectivePermissionDao`

## 목적

역할에 부여할 수 있는 권한을 정의합니다. 공개·인증 전용 API의 추적용 식별자는 역할 부여 대상이 아니므로 이 테이블에 저장하지 않습니다. 같은 권한은 여러 API에서 사용할 수 있습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `permission_code` | `VARCHAR(150)` | 아니요 | 없음 | `도메인:리소스:동작` 권한 코드, 기본 키 |
| `display_name` | `VARCHAR(100)` | 아니요 | 없음 | 화면 표시 이름 |
| `description` | `VARCHAR(500)` | 예 | 없음 | 허용 작업 설명 |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | 생성일 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | 최종 수정일 |
| `created_by` | `VARCHAR(100)` | 아니요 | `'0'` | 생성자, 시스템 작업은 문자열 `'0'` |
| `updated_by` | `VARCHAR(100)` | 아니요 | `'0'` | 최종 수정자, 시스템 작업은 문자열 `'0'` |

[공통 스키마 생성 규칙](../../schema-rules.md)에 따라 생성 정보를 보존하고 수정 정보만 갱신합니다.

## 제약 및 인덱스

2026-11-13: 이미지 기능 카탈로그는 dataset:image:read·upload·update·delete입니다. common R__04_image_permissions.sql에서 멱등 등록하고 local seed만 admin 역할에 네 권한을 연결합니다. 기능별 대상 범위와 현재 API 구현 상태는 [API 계약](../../../api/api.md#이미지-기능-권한)에 둡니다. 후속 ADR-041에서 delete 권한을 사용하는 개별 이미지 삭제 API·선택 삭제 화면을 구현했습니다.

- `pk_auth_permission`: 기본 키 (`permission_code`). 별도 동일 컬럼 인덱스를 만들지 않습니다.
- 코드는 `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$` 형식과 최대 150자를 서비스 입력에서 검증합니다. 향후 관리 API에서 빈 표시 이름도 거부해야 하며 현재 쓰기 API는 없습니다. DB CHECK의 구체 문법은 대상 DB 결정 후 확정합니다.
- 예: `auth:user:manage`, `auth:role:manage`, `dataset:item:read`.
- 코드는 생성 후 변경하지 않습니다. 계층 상속이나 와일드카드를 지원하지 않습니다.
- 초기 조회 정책은 모두 매 요청 DB 조회입니다. 캐시 정책·TTL 컬럼은 8단계 필요성 검토 후 별도 변경으로 정의하며 현재는 추가하지 않습니다.

## 데이터셋 권한 카탈로그

이미지 업로드 추가(2026-11-12): common R__04_image_permissions.sql에서 `dataset:image:upload`를 등록합니다. local R__local_image_permissions.sql은 admin 역할에 해당 권한을 명시적으로 연결합니다. 일반·운영 역할의 자동 부여는 없으며 공간 운영자/데이터셋 생성자도 기능 권한을 보유해야 합니다. 범위는 [ADR-036](../../../decisions/ADR-036-dataset-image-upload.md)을 따릅니다.

현재 사용(2026-11-11): dataset:item:delete도 삭제 서비스에서 명시적으로 검사합니다. local 공간 seed는 admin 역할에 read·create·delete를 연결합니다. 운영·일반 사용자 역할은 자동 생성·할당하지 않으며 사용하려는 역할에 해당 기능 권한을 명시적으로 부여해야 합니다. 공간 내 ADMIN·OWNER 또는 생성자 지위만으로 기능 권한을 대체하지 않습니다. 아래 카탈로그 등록 당시의 미연결 기록과 구분합니다.

2026-11-10: 사용자 요청에 따라 공통 seed `db/seed/app/common/R__03_dataset_permissions.sql`에 다음 권한을 등록합니다. DML이므로 `db/migration/`에 넣지 않으며 기존 app 공통 위치에서 Flyway가 읽습니다.

| 코드 | 표시 이름 | 의미 |
|---|---|---|
| `dataset:item:read` | 데이터셋 조회 | 목록·상세 정보 조회 |
| `dataset:item:create` | 데이터셋 생성 | 생성 |
| `dataset:item:use` | 데이터셋 사용 | 처리·분석 등 업무 입력으로 사용 |
| `dataset:item:update` | 데이터셋 수정 | 정보 수정 |
| `dataset:item:delete` | 데이터셋 삭제 | 삭제 |

없는 코드만 삽입하며 기존 같은 코드의 정의·시각·작업자와 역할 연결은 보존합니다. 신규 행의 생성·수정 시각은 같은 타임스탬프, 생성자·수정자는 시스템 작업자 `'0'`입니다. 재실행해도 기존 행을 변경하지 않습니다. 권한 등록은 기능 구현이나 접근 허용을 의미하지 않습니다. 생성 서비스의 현재 인증 전용 계약은 유지하며 작업별 서비스 선언·워크스페이스 접근 검사는 후속 구현입니다.

공통 seed는 역할에 권한을 부여하지 않습니다. local seed가 최초 실행되면 기존 정책에 따라 그 시점의 모든 카탈로그 권한을 로컬 admin 역할에 연결합니다. 이미 적용된 local seed는 이번 파일 추가만으로 재실행되지 않으므로 기존 admin에 새 권한을 자동 추가하지 않습니다. 실제 로컬·운영 DB에는 이번 작업에서 적용하지 않았습니다.

## 관계 및 삭제 규칙

- `auth_role_permission.permission_code`가 참조합니다. 참조가 남아 있으면 삭제를 거부하고 자동 연쇄 삭제하지 않습니다.
- 실행 중 API가 참조하는 권한 삭제는 서비스·배포 검증으로 금지합니다. API와의 정합성은 FK만으로 보장되지 않습니다.

## 데이터 파일 권한 — 2026-11-13

공통 R__05_file_permissions.sql에 dataset:file:read/upload/update/delete를 등록합니다. 이미지·데이터셋 권한과 독립이며 공통 seed는 역할 부여를 하지 않습니다. local R__local_file_permissions.sql만 admin 역할에 네 권한을 부여합니다. 대상 접근 조건은 [API](../../../api/api.md#데이터-파일-api--2026-11-13)를 따릅니다.
