# `auth_user_role` 테이블 정의

- 그룹: 인증·권한
- 데이터소스: `app`
- 상태: 권한 조회 DAO 구현
- 대응 모델: `com.imageaiplatform.auth.domain.AuthUserRole`
- 조회 계약: `com.imageaiplatform.auth.dao.EffectivePermissionDao`
- 조회 구현: `com.imageaiplatform.auth.dao.impl.MyBatisEffectivePermissionDao`

## 목적

사용자에게 여러 역할을 부여합니다. 사용자 직접 권한, 그룹, 역할 상속은 포함하지 않습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `username` | `VARCHAR(100)` | 아니요 | 없음 | 기존 `auth_user.username` 참조 |
| `role_code` | `VARCHAR(100)` | 아니요 | 없음 | `auth_role.role_code` 참조 |

## 제약 및 인덱스

- `pk_auth_user_role`: 기본 키 (`username`, `role_code`). 동일 역할의 중복 부여를 차단하고 사용자별 역할 조회를 지원합니다.
- `fk_auth_user_role_user`: `username` → `auth_user(username)`.
- `fk_auth_user_role_role`: `role_code` → `auth_role(role_code)`.
- `idx_auth_user_role_role`: (`role_code`, `username`). 역할별 사용자 조회와 역할 삭제 시 참조 확인을 지원합니다.
- FK는 삭제와 키 변경을 허용하지 않는 `NO ACTION`으로 정의합니다. 자동 연쇄 삭제·갱신하지 않습니다.

## 관계 및 삭제 규칙

- 역할 회수는 연결 행 삭제로 표현합니다. 사용자·역할 삭제 전에 연결 행을 명시적으로 정리합니다.
- `username`은 현재 사용자 테이블와 일치시키기 위한 참조 키입니다.
- 계정 비활성화는 연결을 삭제하지 않습니다. 계정 상태 검증에서 접근을 거부합니다.
- 부여자·회수자와 변경 시각은 이후 감사 기록에 남깁니다. 이 테이블은 현재 할당 상태만 보관하며 감사 이력을 대신하지 않습니다.
