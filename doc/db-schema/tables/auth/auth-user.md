# `auth_user` 테이블 정의

- 그룹: 인증
- 데이터소스: `app`
- 생성 마이그레이션: `backend/src/main/resources/db/migration/V1__create_auth_user.sql`
- 사용 코드: `com.imageaiplatform.auth.dao.AuthUserDao`

## 목적

아이디 기반 로그인에 필요한 계정 식별자, BCrypt 비밀번호 해시, 계정 활성 상태를 저장합니다. local/test는 앱 시작 시 Flyway가 V1을 적용하고, dev/prod는 배포 단계의 마이그레이션 전용 실행으로 적용합니다. 계정 행은 자동 생성하지 않습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `username` | `VARCHAR(100)` | 아니요 | 없음 | 로그인 아이디, 기본 키 |
| `password_hash` | `VARCHAR(100)` | 아니요 | 없음 | BCrypt 비밀번호 해시 |
| `enabled` | `BOOLEAN` | 아니요 | `TRUE` | 로그인 허용 여부 |

## 제약 및 인덱스

- 기본 키: `username`
- 별도 인덱스 없음. 사용자명 기본 키 인덱스로 로그인 조회를 처리합니다.
- 평문 비밀번호를 저장하지 않습니다.

## 권한 스키마와의 관계

- [auth_user_role](auth-user-role.md)의 `username`이 이 테이블의 PK를 V2 migration에서 참조합니다.
- 사용자 삭제는 역할 연결을 먼저 해제해야 합니다. 계정 비활성화는 역할 연결을 삭제하지 않습니다.
- 현재 불변 사용자 ID 컬럼은 없습니다. 도입 시 사용자 테이블과 연결 FK를 함께 전환하는 별도 migration이 필요합니다. 이 정의 추가로 기존 V1 스키마가 변경되지는 않습니다.
