# workspace_member 테이블 설계

- 상태: V10 DDL·local seed 구현 — 실제 DB 미적용, 가입·승인 API 미구현
- 마이그레이션: `backend/src/main/resources/db/migration/app/V10__create_workspaces.sql`
- 기준일: 2026-11-11
- 데이터소스: `app`
- 업무 관계: [워크스페이스 테이블 설계](README.md)

## 목적

사용자와 워크스페이스의 다대다 관계 및 현재 가입 상태를 저장합니다. 가입 신청과 승인된 멤버를 하나의 테이블에서 관리하며 재신청 시 같은 행을 갱신합니다. 전체 신청·상태 변경 이력을 보존하는 테이블은 아닙니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| `workspace_id` | `VARCHAR(36)` | 불가 | 없음 | 공간 FK, 복합 PK |
| `user_key` | `VARCHAR(100)` | 불가 | 없음 | 사용자 FK, 복합 PK |
| `role_code` | `VARCHAR(20)` | 불가 | `'MEMBER'` | 공간 내 역할 `ADMIN`, `MEMBER` |
| `status` | `VARCHAR(20)` | 불가 | `'PENDING'` | 현재 가입 상태 |
| `requested_at` | `TIMESTAMP(6) WITH TIME ZONE` | 허용 | 없음 | 최근 신청일시. 최초 소유자 자동 참여는 NULL |
| `reviewed_at` | `TIMESTAMP(6) WITH TIME ZONE` | 허용 | 없음 | 최근 신청 승인·거절 처리일시 |
| `reviewed_by` | `VARCHAR(100)` | 허용 | 없음 | 최근 신청 처리자의 내부 user_key. 처리 당시 감사 값으로 보존, FK 없음 |
| `joined_at` | `TIMESTAMP(6) WITH TIME ZONE` | 허용 | 없음 | 최근 가입 승인일시. 최초 소유자는 공간 생성일시 |
| `created_at` | `TIMESTAMP(6) WITH TIME ZONE` | 불가 | `CURRENT_TIMESTAMP` | 최초 관계 행 생성일시 |
| `updated_at` | `TIMESTAMP(6) WITH TIME ZONE` | 불가 | `CURRENT_TIMESTAMP` | 최종 상태·역할 변경일시 |
| `created_by` | `VARCHAR(100)` | 불가 | `'0'` | 최초 작업자의 내부 user_key |
| `updated_by` | `VARCHAR(100)` | 불가 | `'0'` | 최종 작업자의 내부 user_key |

모든 시각은 날짜와 시간을 합친 타임스탬프입니다. 공통 컬럼은 [공통 스키마 규칙](../../schema-rules.md)을 따릅니다. 요청 본문에서 사용자 키·역할·처리자·시각을 받아 신뢰하지 않습니다.

## 키·제약·인덱스

- `pk_workspace_member`: PK (`workspace_id`, `user_key`). 중복 신청·중복 가입 차단, 별도 회원 UUID 불필요.
- `fk_workspace_member_workspace`: `workspace_id` → `workspace(workspace_id)`.
- `fk_workspace_member_user`: `user_key` → `auth_user(user_key)`.
- 두 FK 모두 삭제·키 변경 `NO ACTION`. 탈퇴·제거는 행 삭제가 아닌 상태 변경.
- `ck_workspace_member_role`: `ADMIN`, `MEMBER`만 허용. 기존 전역 `auth_role`과 독립된 고정 업무 역할이며 FK로 연결하지 않음.
- `ck_workspace_member_status`: `PENDING`, `ACTIVE`, `REJECTED`, `CANCELLED`, `LEFT`, `REMOVED`만 허용.
- `ck_workspace_member_review`: `reviewed_at`, `reviewed_by`는 모두 NULL이거나 모두 값이 있어야 함.
- `ck_workspace_member_joined`: `ACTIVE`, `LEFT`, `REMOVED`는 `joined_at` 필수. `PENDING`, `REJECTED`, `CANCELLED`는 NULL.
- `ck_workspace_member_requested`: 최초 소유자 자동 참여 외 신청·종료 상태(`PENDING`, `REJECTED`, `CANCELLED`)는 `requested_at` 필수.
- `idx_workspace_member_user_status`: (`user_key`, `status`, `workspace_id`). 내 가입 공간·신청 상태 조회.
- `idx_workspace_member_workspace_status`: (`workspace_id`, `status`, `requested_at`, `user_key`). 공간별 승인 대기 목록·멤버 조회.

## 상태 전이 설계안

| 동작 | 전이 | 기록 규칙 |
|---|---|---|
| 최초 가입 신청 | 행 없음 → PENDING | 역할 MEMBER, requested_at 기록, 승인·가입 정보 NULL |
| 신청 승인 | PENDING → ACTIVE | 역할 MEMBER, reviewed_at·reviewed_by·joined_at 기록 |
| 신청 거절 | PENDING → REJECTED | reviewed_at·reviewed_by 기록, joined_at NULL |
| 신청 취소 | PENDING → CANCELLED | 신청자가 취소, 승인·가입 정보 NULL |
| 자진 탈퇴 | ACTIVE → LEFT | 기존 가입·승인 정보 유지, 소유자는 이전 전 탈퇴 불가 |
| 관리자 제거 | ACTIVE → REMOVED | 기존 가입·승인 정보 유지, 소유자 제거 불가 |
| 재신청 | REJECTED / CANCELLED / LEFT → PENDING | 최근 신청일 갱신, 역할 MEMBER로 초기화, 승인·가입 정보 NULL |

REMOVED 사용자의 직접 재신청은 차단하고 관리자 재가입 허용 방식은 후속 정책으로 남깁니다. PENDING·ACTIVE 중복 신청은 새 행을 만들거나 승인 정보를 초기화하지 않습니다. 모든 변경은 updated_at·updated_by를 기록하고 created_at·created_by는 유지합니다. 승인·거절은 공간 관리자 자격과 PENDING 상태를 함께 확인하고 조건부 갱신합니다.

최초 소유자 자동 참여는 ACTIVE·ADMIN, joined_at은 공간 생성일시이며 requested_at·reviewed_at·reviewed_by는 NULL입니다. 일반 신청 승인처럼 임의의 심사자를 만들지 않습니다. 계정 비활성화는 멤버십 행을 지우지 않으며 서버의 활성 계정 검사에서 접근을 차단합니다.

ADMIN 승격·강등은 ACTIVE 멤버를 대상으로 소유자만 수행하는 안입니다. 승인자 감사 정보는 역할 변경으로 덮어쓰지 않습니다. 탈퇴·제거·역할 변경의 마지막 작업자는 공통 수정 컬럼에 남지만 전체 이력은 보존하지 않습니다.
