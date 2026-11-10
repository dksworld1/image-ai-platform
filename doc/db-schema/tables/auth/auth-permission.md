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

- `pk_auth_permission`: 기본 키 (`permission_code`). 별도 동일 컬럼 인덱스를 만들지 않습니다.
- 코드는 `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$` 형식과 최대 150자를 서비스 입력에서 검증합니다. 향후 관리 API에서 빈 표시 이름도 거부해야 하며 현재 쓰기 API는 없습니다. DB CHECK의 구체 문법은 대상 DB 결정 후 확정합니다.
- 예: `auth:user:manage`, `auth:role:manage`, `dataset:item:read`.
- 코드는 생성 후 변경하지 않습니다. 계층 상속이나 와일드카드를 지원하지 않습니다.
- 초기 조회 정책은 모두 매 요청 DB 조회입니다. 캐시 정책·TTL 컬럼은 8단계 필요성 검토 후 별도 변경으로 정의하며 현재는 추가하지 않습니다.

## 관계 및 삭제 규칙

- `auth_role_permission.permission_code`가 참조합니다. 참조가 남아 있으면 삭제를 거부하고 자동 연쇄 삭제하지 않습니다.
- 실행 중 API가 참조하는 권한 삭제는 서비스·배포 검증으로 금지합니다. API와의 정합성은 FK만으로 보장되지 않습니다.
