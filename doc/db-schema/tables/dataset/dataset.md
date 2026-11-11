# dataset 테이블 정의

- 상태: 생성 구현
- 현재 공간 관계(2026-11-11): V14~V16으로 dataset.workspace_id 단일 FK 복원·공유 연결 제거. 여러 공간 선택 시 ID가 다른 독립 데이터셋을 각각 생성합니다. [ADR-034](../../../decisions/ADR-034-independent-dataset-creation.md)를 따릅니다.
- 과거 local 데이터: 공간 미지정 행만 V17에서 첫 번째 공통 공간으로 배정하며 기존 귀속은 유지합니다. 생성 정보 보존·수정 정보 시스템 이관 기록. 실제 DB 미적용.
- 갱신일: 2026-11-10
- 스키마 추가: 2026-11-11 V10에서 nullable workspace_id·FK·조회 인덱스 정의, 생성 API 공간 전환은 미구현
- 데이터소스: app
- 마이그레이션: `backend/src/main/resources/db/migration/app/V6__create_dataset.sql`
- 대응 모델: `com.imageaiplatform.dataset.domain.Dataset`

## 목적

이름·설명으로 생성한 데이터셋을 저장합니다. 이름은 식별자가 아니며 중복을 허용합니다. 별도 소유자·공유 범위는 현재 정의하지 않습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| dataset_id | VARCHAR(36) | 불가 | 없음 | 서버에서 발급한 UUID 문자열, PK |
| name | VARCHAR(200) | 불가 | 없음 | 필수 이름, 앞뒤 공백 제거 |
| description | VARCHAR(2000) | 허용 | 없음 | 선택 설명, 앞뒤 공백 제거·빈 값은 NULL |
| workspace_id | VARCHAR(36) | 허용 | 없음 | 귀속 workspace FK. 신규 생성 필수, 과거 미귀속 데이터만 NULL 허용 |
| created_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 생성 날짜와 시간을 함께 저장한 생성일시 |
| updated_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 최종 수정일시, 생성 시 created_at과 동일 |
| created_by | VARCHAR(100) | 불가 | '0' | 서버 인증 정보의 내부 user_key |
| updated_by | VARCHAR(100) | 불가 | '0' | 생성 시 created_by와 동일 |

## 기록 규칙과 제약

- 생성일과 생성시간을 DATE·TIME이나 문자열 컬럼으로 분리하지 않습니다. UTC 절대 시점을 마이크로초 정밀도 타임스탬프로 저장하고 Java에서는 OffsetDateTime을 사용합니다.
- 공통 컬럼은 [공통 스키마 규칙](../../schema-rules.md)을 따릅니다. 시스템 INSERT용 공통 기본값은 유지하되 생성 API의 INSERT는 모든 값을 명시해 기본값에 의존하지 않습니다.
- 서비스가 시각을 한 번 얻어 생성일시·수정일시에 동일하게 전달합니다. ID·작업자·시각을 생성 요청에서 받지 않습니다. 사용자 생성 API는 내부 user_key를 기록하며 시스템 예약값 `'0'`으로 대체하지 않습니다. user_id·username 변경 시에도 감사 값은 바뀌지 않습니다. [ADR-031](../../../decisions/ADR-031-user-identity-fields.md)을 따릅니다.
- PK는 pk_dataset(dataset_id)이며 이름 고유 제약·작업자 FK는 없습니다. fk_dataset_workspace는 workspace 참조·삭제/변경 NO ACTION, idx_dataset_workspace_created는 (workspace_id, created_at DESC, dataset_id ASC)입니다.
- 길이 제한은 공백 제거 전 입력 길이에 적용합니다. 빈 이름과 공백만 있는 이름은 거부합니다. HTTP 입력과 도메인 모두 유효성을 확인합니다.

## 워크스페이스 귀속 변경 설계안

아래는 V10 도입·V13 제거 당시의 이력입니다. 현재 V14에서 FK·인덱스를 복원했으며 신규 생성은 한 공간에만 귀속합니다. 공간별 생성 행의 최소 1개 공간은 서비스·도메인에서 보장하고, 미귀속 기존 데이터 때문에 DB NOT NULL은 아직 적용하지 않았습니다. V15는 기존 공유 데이터의 이름·설명·감사 값을 공간별로 보존하며 V16에서 dataset_workspace를 제거합니다.

2026-11-11: V10에서 workspace_id·FK·인덱스를 추가했습니다. 현재 컬럼은 전환용 nullable이며 아래 NOT NULL은 후속 목표입니다. 생성 API에는 아직 공간 입력·검사를 적용하지 않았습니다. [워크스페이스 테이블 설계](../workspace/README.md)와 [ADR-032](../../../decisions/ADR-032-workspace-data-boundary.md)를 따릅니다.

| 추가 컬럼 | 형식 | 최종 NULL | 기본값 | 설명 |
|---|---|---|---|---|
| `workspace_id` | `VARCHAR(36)` | 불가 | 없음 | 데이터 귀속 공간, `workspace.workspace_id` FK |

- `fk_dataset_workspace`: 공간 참조, 삭제·키 변경 `NO ACTION`. 공간 삭제로 데이터셋을 자동 삭제하지 않습니다.
- `idx_dataset_workspace_created`: (`workspace_id`, `created_at DESC`, `dataset_id ASC`). 공간별 최신 생성순 목록과 동률 정렬 지원.
- 목록은 workspace_id, 상세·수정·삭제는 workspace_id와 dataset_id를 함께 조건으로 사용합니다. 사용 요청도 대상 공간·기능 권한을 검사합니다.
- 최초 추가 시 nullable로 이행하고 기존 데이터의 공간을 명시적으로 매핑한 뒤 NOT NULL로 전환합니다. 임의 기본 공간·생성자의 가입 공간을 자동 배정하지 않습니다.
- created_by는 생성자 감사 값으로 유지하며 소유·조회 범위로 사용하지 않습니다. 생성자의 탈퇴·비활성화 후에도 데이터는 공간에 남습니다.
- 생성 API·프런트 입력 흐름·서비스 권한과 NOT NULL 전환은 후속 작업입니다. V10은 기존 데이터셋을 임의 공간에 배정하지 않으며 현재 생성 API도 workspace_id 없이 저장할 수 있습니다. 실제 DB에는 아직 적용하지 않았습니다.

## 관련 문서

현재 삭제는 [ADR-035](../../../decisions/ADR-035-dataset-deletion-authorization.md)에 따라 실제 귀속 공간의 ACTIVE 멤버십과 운영자/생성자 조건을 적용한 물리 DELETE입니다. 공간별 독립 행을 함께 삭제하지 않습니다. 이번 구현에 이미지·파일 처리는 포함하지 않습니다. 삭제 이력 테이블과 추가 컬럼은 만들지 않았습니다.

- [API 계약](../../../api/api.md#데이터셋-생성)
- [ADR-030](../../../decisions/ADR-030-dataset-creation-contract.md)
