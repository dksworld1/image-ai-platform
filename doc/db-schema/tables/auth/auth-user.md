# `auth_user` 테이블 정의

- 그룹: 인증
- 데이터소스: `app`
- 사용 코드: `com.imageaiplatform.auth.dao.AuthUserDao`

## 목적

변경 불가 내부 키(user_key), 로그인 아이디(user_id), 실명(username), BCrypt 비밀번호 해시와 활성 상태를 저장합니다. 계정 생성 API는 제공하지 않습니다. local seed는 로그인 아이디 admin·예시 이름 로컬 관리자를 준비하며 새 계정의 내부 키는 UUID입니다. [ADR-031](../../../decisions/ADR-031-user-identity-fields.md)을 따릅니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `user_key` | `VARCHAR(100)` | 아니요 | 없음 | 변경하지 않는 내부 키, 기본 키 |
| `user_id` | `VARCHAR(100)` | 아니요 | 없음 | 로그인 아이디, UNIQUE |
| `username` | `VARCHAR(100)` | 예 | 없음 | 실제 이름, 중복 허용. 기존 데이터의 실명 미상은 NULL |
| `password_hash` | `VARCHAR(100)` | 아니요 | 없음 | BCrypt 비밀번호 해시 |
| `enabled` | `BOOLEAN` | 아니요 | `TRUE` | 로그인 허용 여부 |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | 생성일 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | 최종 수정일 |
| `created_by` | `VARCHAR(100)` | 아니요 | `'0'` | 생성자, 시스템 작업은 문자열 `'0'` |
| `updated_by` | `VARCHAR(100)` | 아니요 | `'0'` | 최종 수정자, 시스템 작업은 문자열 `'0'` |

[공통 스키마 생성 규칙](../../schema-rules.md)에 따라 생성 정보를 보존하고 수정 정보만 갱신합니다.

## 제약 및 인덱스

- 기본 키: `user_key`
- `uq_auth_user_user_id`: 로그인 아이디 고유 제약. 로그인은 이 값으로 조회합니다.
- `ck_auth_user_reserved_user_key`: 시스템 내부 키 `'0'`의 계정 생성을 거부합니다.
- 실제 이름에는 고유 제약을 두지 않습니다. user_key는 내부 참조·JWT, user_id는 로그인, username은 실명 표시 용도로 구분합니다.
- 평문 비밀번호를 저장하지 않습니다.

## 권한 스키마와의 관계

- [auth_user_role](auth-user-role.md)의 `user_key`가 이 테이블의 PK를 참조합니다.
- 사용자 삭제는 역할 연결을 먼저 해제해야 합니다. 계정 비활성화는 역할 연결을 삭제하지 않습니다.
- 공통 생성자·수정자도 user_key를 기록하지만 FK는 설정하지 않습니다. 로그인 아이디·실명 변경과 기존 감사 기록은 독립적입니다.
- V7→V8→V9에서 기존 username 값을 user_key와 초기 user_id로 보존합니다. 실명 username은 NULL이며 과거 실명을 임의로 만들지 않습니다. 이전 버전 SQL은 이력으로 유지합니다.
