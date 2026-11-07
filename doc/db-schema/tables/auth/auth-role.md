# `auth_role` 테이블 정의

- 그룹: 인증·권한
- 데이터소스: `app`
- 상태: V2 migration·권한 조회 DAO 구현 (실제 적용은 환경별 migration 정책에 따름)
- 생성 마이그레이션: `backend/src/main/resources/db/migration/V2__create_auth_rbac.sql`
- 대응 모델: `com.imageaiplatform.auth.domain.AuthRole`
- 조회 계약: `com.imageaiplatform.auth.dao.EffectivePermissionDao`
- 조회 구현: `com.imageaiplatform.auth.dao.impl.MyBatisEffectivePermissionDao`

## 목적

사용자에게 부여하는 역할을 정의합니다. V3에서 초기 `ADMIN`·`MEMBER` 두 역할을 system_role=TRUE로 추가하며, 같은 코드의 기존 행은 덮어쓰지 않습니다. V4는 ADMIN에 `auth:user:manage`만 연결합니다. 최초 계정과 사용자 역할 할당은 일반 서버 시작 시 생성하지 않고 별도 bootstrap 명령으로 생성합니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `role_code` | `VARCHAR(100)` | 아니요 | 없음 | 변경하지 않는 역할 코드, 기본 키 |
| `display_name` | `VARCHAR(100)` | 아니요 | 없음 | 화면 표시 이름 |
| `description` | `VARCHAR(500)` | 예 | 없음 | 역할 설명 |
| `system_role` | `BOOLEAN` | 아니요 | `FALSE` | 시스템 관리 역할 여부 |

## 제약 및 인덱스

- `pk_auth_role`: 기본 키 (`role_code`). 별도 동일 컬럼 인덱스를 만들지 않습니다.
- 역할 코드는 `^[A-Z][A-Z0-9_]*$` 형식입니다. 향후 관리 API에서 검증하며 현재 쓰기 API는 없습니다. 빈 코드·이름과 길이 초과를 거부합니다. DB CHECK의 구체 문법은 대상 DB 결정 후 확정합니다.
- 표시 이름은 식별자가 아니므로 중복을 허용합니다. `role_code`는 생성 후 변경하지 않습니다.
- `system_role`은 수정·삭제 보호를 위한 표시입니다. 이 값이나 `ADMIN`이라는 이름만으로 모든 권한을 허용하지 않습니다.

## 관계 및 삭제 규칙

- `auth_user_role.role_code`, `auth_role_permission.role_code`가 참조합니다.
- 참조가 남아 있으면 삭제를 거부합니다. 연결 해제 후 역할 삭제는 서비스 트랜잭션에서 수행하며 자동 연쇄 삭제하지 않습니다.
- UserRoleService의 ADMIN 부여·회수, UserAccountService의 활성 상태 변경, 최초 관리자 생성은 ADMIN 행을 FOR UPDATE로 잠급니다. 현재 활성 ADMIN이 한 명이면 해당 역할 회수·비활성화를 거부합니다. UserRoleDao는 트랜잭션과 READ_COMMITTED 격리 수준을 확인합니다. 시스템 역할 자체의 수정·삭제 API는 없으며, 직접 SQL을 차단하는 DB 트리거는 추가하지 않습니다.
