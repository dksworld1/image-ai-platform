# `auth_audit_log` 테이블 정의

- 그룹: 인증·권한
- 데이터소스: `app`
- 생성 마이그레이션: `backend/src/main/resources/db/migration/V3__seed_roles_and_create_auth_audit_log.sql`
- 저장 코드: `com.imageaiplatform.auth.dao.impl.MyBatisAuthAuditDao`
- 호출 서비스: `com.imageaiplatform.auth.service.UserRoleService`

## 목적

사용자 역할 부여·회수의 실제 변경 이력을 저장합니다. 역할 변경과 같은 app 트랜잭션에서 추가하며, 감사 저장에 실패하면 역할 변경도 롤백합니다. 로그인·조회·실패 시도 및 역할별 권한 변경 이력은 현재 범위에 포함하지 않습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `id` | `BIGINT` | 아니요 | 자동 생성 IDENTITY | 기록 식별자, 기본 키 |
| `occurred_at` | `TIMESTAMP WITH TIME ZONE` | 아니요 | `CURRENT_TIMESTAMP` | DB가 기록하는 시각 |
| `actor_username` | `VARCHAR(100)` | 아니요 | 없음 | 서버에서 인증·인가한 수행자 이름 스냅샷 |
| `target_username` | `VARCHAR(100)` | 아니요 | 없음 | 역할 변경 대상 사용자 이름 스냅샷 |
| `role_code` | `VARCHAR(100)` | 아니요 | 없음 | 변경한 역할 코드 스냅샷 |
| `action` | `VARCHAR(30)` | 아니요 | 없음 | `ROLE_GRANTED` 또는 `ROLE_REVOKED` |

## 제약 및 인덱스

- `pk_auth_audit_log`: 기본 키 (`id`). 롤백으로 번호가 비어도 정상이며 번호가 커밋 순서를 보장하지는 않습니다.
- `ck_auth_audit_log_action`: 역할 부여·회수 두 값만 허용합니다.
- `idx_auth_audit_log_target`: (`target_username`, `occurred_at`, `id`). 대상별 이력 조회를 위한 인덱스입니다. 조회 API는 아직 없습니다.
- 사용자·역할 FK를 두지 않습니다. 계정·역할이 삭제되거나 변경되어도 당시 이름과 이력을 보존합니다.

## 기록·삭제 정책

- 앱 DAO는 추가 기능만 제공합니다. 수정·삭제 API와 보존 기간별 정리 작업은 구현하지 않습니다.
- 이미 부여된 역할의 재부여, 없는 연결의 회수는 실제 변경이 아니므로 새 이력을 만들지 않습니다.
- 실패·거부된 작업은 성공 이력으로 남기지 않습니다. 동시 요청 시 중복 PK 오류 등으로 실패한 트랜잭션도 성공 기록을 남기지 않습니다.
- 비밀번호·JWT를 저장하지 않습니다. DB 관리자에 의한 수정을 막는 변조 방지 저장소까지 구현한 것은 아닙니다.
- 최초 관리자 생성은 `BOOTSTRAP` 수행자의 `ROLE_GRANTED`로 기록합니다. 계정·ADMIN 할당·감사를 하나의 트랜잭션으로 저장합니다. 계정 활성 상태 변경은 현재 역할 전용 감사 범위에 포함하지 않습니다.
