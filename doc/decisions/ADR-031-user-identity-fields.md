# ADR-031: 내부 사용자 키·로그인 아이디·실명 분리

- 상태: 채택
- 날짜: 2026-11-10
- 대체/관련 기록: ADR-009·025의 사용자 필드 계약, ADR-028·030의 작업자 식별 기준을 부분 대체

## 맥락

사용자가 username은 자연인의 실제 이름, user_id는 로그인 아이디, user_key는 내부 식별자로 정의하고 모든 관련 구성요소의 전환을 요청했습니다. 기존 username은 PK·JWT subject·역할 연결·감사 컬럼에 사용되므로 값과 관계를 보존해야 합니다.

## 대안

- 로그인 아이디를 내부 키로 계속 사용: 의미가 섞이며 로그인 아이디 변경이 참조에 영향을 줍니다.
- 내부 키를 전부 재발급: 기존 역할·감사 값의 일괄 변환과 삭제 계정의 과거 기록 처리까지 필요합니다.
- 기존 내부 참조값을 보존하고 필드의 역할을 분리: 이관 과정에서 관계와 기록을 유지하고 이후 로그인 아이디를 독립적으로 바꿀 수 있습니다.

## 결정

| 필드 | 형식 | 의미 |
|---|---|---|
| user_key | VARCHAR(100), PK, NOT NULL | 변경하지 않는 내부 키. 새 local 계정은 UUID 문자열 사용 |
| user_id | VARCHAR(100), UNIQUE, NOT NULL | 로그인 아이디 |
| username | VARCHAR(100), NULL 허용 | 실명. 중복 허용, 알 수 없으면 NULL |

JWT sub·SecurityContext의 이름·역할 연결·생성자·수정자는 user_key를 사용합니다. 시스템 작업자 키는 문자열 `'0'`이며 해당 내부 키의 계정은 만들지 않습니다. 감사 컬럼은 사용자 FK를 두지 않아 삭제된 계정과 시스템 작업 기록도 보존합니다. 실명은 인증·권한 판정 근거로 사용하지 않습니다.

로그인 요청은 userId와 password, 응답은 기존 토큰 필드와 userKey·userId·username입니다. 서버가 반환한 값을 프런트 세션에 보관하고 헤더에는 로그인 아이디와 존재하는 실명을 각각 표시합니다. 이전 username 전용 세션은 제거하고 재로그인합니다. HTML autocomplete=username과 datasource.username은 표준 기술 설정이므로 변경하지 않습니다.

## 이관 순서

1. V7 DDL: 기존 auth_user.username과 auth_user_role.username을 user_key로 이름 변경하고 user_id·실명 username을 추가합니다.
2. V8 DML: `db/seed/app/common/identity/V8__backfill_user_ids.sql`에서 기존 user_key 값을 user_id로 복사합니다. 실제 이름은 추정하지 않고 NULL을 유지합니다. 이관을 업무 수정으로 간주해 과거 감사 정보를 덮어쓰지 않습니다.
3. V9 DDL: user_id의 NOT NULL·UNIQUE를 적용합니다. 기존 PK·FK와 감사 값은 동일한 문자열을 계속 참조합니다.

V1~V6는 유지합니다. versioned DML이 V7과 V9 사이에 실행되도록 app 스키마와 common seed 위치를 반드시 함께 지정합니다. 기본 local/test 설정은 이미 두 위치를 포함합니다. local 반복 seed는 user_id로 계정을 찾아 기존 user_key를 유지하고, 신규 계정만 UUID를 발급합니다. 사용자 등록·개명·아이디 변경 API는 추가하지 않습니다.

## 결과

기존 키가 과거 로그인 아이디와 같은 문자열이어도 이후에는 변경하지 않는 내부 키로만 다룹니다. 로그인 아이디와 실명이 바뀌어도 기존 JWT의 키, 역할·감사 참조는 바뀌지 않습니다. 기존 JWT sub가 이관된 키와 같으면 같은 계정을 계속 가리키며 새로운 로그인 아이디로 다시 해석하지 않습니다.

프런트와 백엔드의 로그인 계약을 함께 배포해야 합니다. 기존 username 요청은 userId가 없어 400이 됩니다. 실제 DB 적용 전 백업하고 동일 Flyway 위치로 전체 V7→V8→V9를 적용합니다. 부분 적용·repair·실제 로컬 DB 변경은 이번 작업에서 수행하지 않습니다.

- [사용자 테이블](../db-schema/tables/auth/auth-user.md)
- [공통 작업자 규칙](../db-schema/schema-rules.md)
- [API 계약](../api/api.md)
- [배포 절차](../operations/deployment.md)
