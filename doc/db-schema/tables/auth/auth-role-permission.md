# `auth_role_permission` 테이블 정의

- 그룹: 인증·권한
- 데이터소스: `app`
- 상태: V2 migration·권한 조회 DAO 구현 (실제 적용은 환경별 migration 정책에 따름)
- 생성 마이그레이션: `backend/src/main/resources/db/migration/V2__create_auth_rbac.sql`
- 대응 모델: `com.imageaiplatform.auth.domain.AuthRolePermission`
- 조회 계약: `com.imageaiplatform.auth.dao.EffectivePermissionDao`
- 조회 구현: `com.imageaiplatform.auth.dao.impl.MyBatisEffectivePermissionDao`

## 목적

역할에 여러 권한을 연결합니다. 사용자의 유효 권한은 보유 역할에 연결된 권한의 합집합입니다. 역할이나 연결이 없으면 부여 권한은 없습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `role_code` | `VARCHAR(100)` | 아니요 | 없음 | `auth_role.role_code` 참조 |
| `permission_code` | `VARCHAR(150)` | 아니요 | 없음 | `auth_permission.permission_code` 참조 |

## 제약 및 인덱스

- `pk_auth_role_permission`: 기본 키 (`role_code`, `permission_code`). 중복 연결을 차단하고 역할별 권한 조회를 지원합니다.
- `fk_auth_role_permission_role`: `role_code` → `auth_role(role_code)`.
- `fk_auth_role_permission_permission`: `permission_code` → `auth_permission(permission_code)`.
- `idx_auth_role_permission_permission`: (`permission_code`, `role_code`). 권한별 역할 조회와 권한 삭제 시 참조 확인을 지원합니다.
- FK는 삭제와 키 변경을 허용하지 않는 `NO ACTION`으로 정의합니다. 자동 연쇄 삭제·갱신하지 않습니다.

## 관계 및 삭제 규칙

- 권한 회수는 연결 행 삭제로 표현합니다. 역할·권한 삭제 전에 연결 행을 명시적으로 정리합니다.
- 연결 변경은 해당 역할을 보유한 모든 사용자에게 영향을 줍니다. 민감한 작업과의 실행 순서·공통 잠금은 5단계에서 정의하며 이 복합 키만으로 동시성 통제가 완성되지는 않습니다.
- 변경 이력은 별도 감사 기록에서 관리할 예정입니다.
