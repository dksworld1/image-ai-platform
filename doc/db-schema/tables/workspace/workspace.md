# workspace 테이블 설계

- 상태: V10 DDL·local seed 구현 — 실제 DB 미적용, 관리 API 미구현
- 마이그레이션: `backend/src/main/resources/db/migration/app/V10__create_workspaces.sql`
- 기준일: 2026-11-11
- 데이터소스: `app`
- 업무 관계: [워크스페이스 테이블 설계](README.md)

## 목적

데이터와 업무를 공유하는 공간입니다. 식별자는 서버 UUID 문자열이며 이름 중복을 허용합니다. 이름이나 생성자를 공간 식별·소유권 판정에 사용하지 않습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| `workspace_id` | `VARCHAR(36)` | 불가 | 없음 | 서버 생성 UUID, PK |
| `name` | `VARCHAR(200)` | 불가 | 없음 | 공백 제거 후 비어 있지 않은 공간 이름 |
| `description` | `VARCHAR(2000)` | 허용 | 없음 | 선택 설명, 공백만 있으면 NULL |
| `owner_user_key` | `VARCHAR(100)` | 불가 | 없음 | 단일 소유자, `auth_user.user_key` FK |
| `status` | `VARCHAR(20)` | 불가 | `'ACTIVE'` | `ACTIVE` 또는 `ARCHIVED` |
| `created_at` | `TIMESTAMP(6) WITH TIME ZONE` | 불가 | `CURRENT_TIMESTAMP` | 생성일시 |
| `updated_at` | `TIMESTAMP(6) WITH TIME ZONE` | 불가 | `CURRENT_TIMESTAMP` | 최종 수정일시 |
| `created_by` | `VARCHAR(100)` | 불가 | `'0'` | 생성 작업자 내부 user_key |
| `updated_by` | `VARCHAR(100)` | 불가 | `'0'` | 최종 수정 작업자 내부 user_key |

공통 컬럼은 [공통 스키마 규칙](../../schema-rules.md)을 따릅니다. 생성 시 동일한 시각·작업자를 명시하며 수정 시 생성 정보를 보존합니다. Java의 시각은 `OffsetDateTime`, 작업자는 `String`입니다.

## 키·제약·인덱스

- `pk_workspace`: PK (`workspace_id`).
- `fk_workspace_owner`: `owner_user_key` → `auth_user(user_key)`, 삭제·변경 `NO ACTION`.
- `ck_workspace_status`: `ACTIVE`, `ARCHIVED`만 허용.
- `idx_workspace_owner`: (`owner_user_key`). 소유 공간 조회·사용자 삭제 시 참조 확인.
- 이름 UNIQUE나 별도 검색 인덱스는 추가하지 않습니다. 검색 방식 확정 후 필요한 경우 검토합니다.
- 공통 `created_by`, `updated_by`에는 FK를 두지 않습니다. 업무 관계인 소유자 FK와 구분합니다.

## 소유자와 운영 상태

소유자는 `owner_user_key` 한 컬럼으로 관리합니다. 멤버십에는 `OWNER` 역할을 중복 저장하지 않습니다. 소유자도 해당 공간의 ACTIVE 멤버 행을 가져야 하며 그 행의 역할은 ADMIN으로 둡니다. 화면과 업무 판정에서 `owner_user_key` 일치 여부로 OWNER를 식별합니다.

이로써 DB의 NOT NULL FK가 존재하는 한 공간에는 정확히 한 소유자가 있습니다. 다만 **소유자의 ACTIVE 멤버십 여부는 이 FK만으로 보장되지 않습니다.** 공간 생성과 소유자 멤버 등록을 같은 트랜잭션에서 수행하고, 소유자 이전·탈퇴·역할 변경도 서비스에서 불변 조건을 지킵니다. 순환 FK·트리거는 추가하지 않습니다.

소유자 이전 시 공간 행을 잠그고 대상의 활성 계정·ACTIVE 멤버십을 확인한 뒤 새 소유자를 ADMIN으로 설정하고 소유자를 변경합니다. 이전 소유자는 ADMIN으로 남기는 안이며, 소유자 본인만 이전할 수 있습니다. 이전 전 소유자 탈퇴·제거·강등은 거부합니다. 현재 소유자의 계정 비활성화는 활성 소유자에게 이전한 후 허용하는 안입니다. 이러한 사용자 관리 연동도 구현 범위에 포함해야 합니다.

`ARCHIVED`는 공간과 데이터의 보존 상태입니다. 일반 멤버의 가입·데이터 업무를 차단하며 소유자의 관리·복원만 별도 정책으로 허용하는 안입니다. 물리 삭제나 자동 연쇄 삭제를 뜻하지 않습니다. 실제 폐쇄 화면·API는 후속 구현입니다.
