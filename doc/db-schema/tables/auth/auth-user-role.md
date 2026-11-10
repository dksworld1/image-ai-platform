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
| `user_key` | `VARCHAR(100)` | 아니요 | 없음 | 기존 `auth_user.user_key` 참조 |
| `role_code` | `VARCHAR(100)` | 아니요 | 없음 | `auth_role.role_code` 참조 |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | 생성일 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | 최종 수정일 |
| `created_by` | `VARCHAR(100)` | 아니요 | `'0'` | 생성자, 시스템 작업은 문자열 `'0'` |
| `updated_by` | `VARCHAR(100)` | 아니요 | `'0'` | 최종 수정자, 시스템 작업은 문자열 `'0'` |

[공통 스키마 생성 규칙](../../schema-rules.md)에 따라 생성 정보를 보존하고 수정 정보만 갱신합니다.

## 제약 및 인덱스

- `pk_auth_user_role`: 기본 키 (`user_key`, `role_code`). 동일 역할의 중복 부여를 차단하고 사용자별 역할 조회를 지원합니다.
- `fk_auth_user_role_user`: `user_key` → `auth_user(user_key)`.
- `fk_auth_user_role_role`: `role_code` → `auth_role(role_code)`.
- `idx_auth_user_role_role`: (`role_code`, `user_key`). 역할별 사용자 조회와 역할 삭제 시 참조 확인을 지원합니다.
- FK는 삭제와 키 변경을 허용하지 않는 `NO ACTION`으로 정의합니다. 자동 연쇄 삭제·갱신하지 않습니다.

## 관계 및 삭제 규칙

- 역할 회수는 연결 행 삭제로 표현합니다. 사용자·역할 삭제 전에 연결 행을 명시적으로 정리합니다.
- `user_key`은 현재 사용자 테이블와 일치시키기 위한 참조 키입니다.
- 계정 비활성화는 연결을 삭제하지 않습니다. 계정 상태 검증에서 접근을 거부합니다.
- 생성·최종 수정 정보는 공통 컬럼에 기록합니다. 회수 시 행을 삭제하므로 회수자·회수 시각은 보존하지 않으며 별도 감사 이력을 대신하지 않습니다.
