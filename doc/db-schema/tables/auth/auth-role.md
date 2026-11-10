# `auth_role` 테이블 정의

- 그룹: 인증·권한
- 데이터소스: `app`
- 상태: 권한 조회 DAO 구현
- 대응 모델: `com.imageaiplatform.auth.domain.AuthRole`
- 조회 계약: `com.imageaiplatform.auth.dao.EffectivePermissionDao`
- 조회 구현: `com.imageaiplatform.auth.dao.impl.MyBatisEffectivePermissionDao`

## 목적

사용자에게 부여하는 역할을 정의합니다. `ADMIN`, `MEMBER`는 후보이며 역할 데이터와 부여 정책은 아직 확정하지 않았습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `role_code` | `VARCHAR(100)` | 아니요 | 없음 | 변경하지 않는 역할 코드, 기본 키 |
| `display_name` | `VARCHAR(100)` | 아니요 | 없음 | 화면 표시 이름 |
| `description` | `VARCHAR(500)` | 예 | 없음 | 역할 설명 |
| `system_role` | `BOOLEAN` | 아니요 | `FALSE` | 시스템 관리 역할 여부 |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | 생성일 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | 최종 수정일 |
| `created_by` | `VARCHAR(100)` | 아니요 | `'0'` | 생성자, 시스템 작업은 문자열 `'0'` |
| `updated_by` | `VARCHAR(100)` | 아니요 | `'0'` | 최종 수정자, 시스템 작업은 문자열 `'0'` |

[공통 스키마 생성 규칙](../../schema-rules.md)에 따라 생성 정보를 보존하고 수정 정보만 갱신합니다.

## 제약 및 인덱스

- `pk_auth_role`: 기본 키 (`role_code`). 별도 동일 컬럼 인덱스를 만들지 않습니다.
- 역할 코드는 `^[A-Z][A-Z0-9_]*$` 형식입니다. 향후 관리 API에서 검증하며 현재 쓰기 API는 없습니다. 빈 코드·이름과 길이 초과를 거부합니다. DB CHECK의 구체 문법은 대상 DB 결정 후 확정합니다.
- 표시 이름은 식별자가 아니므로 중복을 허용합니다. `role_code`는 생성 후 변경하지 않습니다.
- `system_role`은 수정·삭제 보호를 위한 표시입니다. 이 값이나 `ADMIN`이라는 이름만으로 모든 권한을 허용하지 않습니다.

## 관계 및 삭제 규칙

- `auth_user_role.role_code`, `auth_role_permission.role_code`가 참조합니다.
- 참조가 남아 있으면 삭제를 거부합니다. 연결 해제 후 역할 삭제는 서비스 트랜잭션에서 수행하며 자동 연쇄 삭제하지 않습니다.
- 시스템 역할 변경 제한과 마지막 관리자 보호는 서비스에서 구현합니다. FK만으로 보장하지 않습니다.
