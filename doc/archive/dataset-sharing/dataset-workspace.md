# dataset_workspace 테이블 정의

- 상태: 폐기 — V15에서 공간별 독립 행으로 이관 후 V16에서 제거. [ADR-034](../../decisions/ADR-034-independent-dataset-creation.md)로 대체. 아래는 과거 구조
- 기준일: 2026-11-11
- 데이터소스: app
- 관련 결정: [ADR-033](../../decisions/ADR-033-dataset-multiple-workspaces.md)

## 목적과 컬럼

하나의 데이터셋을 여러 워크스페이스에서 공동 사용합니다. 데이터셋을 공간별로 복제하지 않습니다.

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| dataset_id | VARCHAR(36) | 불가 | 없음 | dataset FK, 복합 PK |
| workspace_id | VARCHAR(36) | 불가 | 없음 | workspace FK, 복합 PK |
| created_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 연결 생성일시 |
| updated_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 연결 최종 수정일시 |
| created_by | VARCHAR(100) | 불가 | '0' | 생성 작업자 내부 user_key |
| updated_by | VARCHAR(100) | 불가 | '0' | 수정 작업자 내부 user_key |

## 제약·인덱스와 저장

- `pk_dataset_workspace`: (`dataset_id`, `workspace_id`). 같은 공간의 중복 연결을 차단합니다.
- `fk_dataset_workspace_dataset`, `fk_dataset_workspace_space`: 각각 dataset·workspace 참조, 삭제·키 변경 `NO ACTION`.
- `idx_dataset_workspace_space`: (`workspace_id`, `dataset_id`). 공간별 데이터 조회·참조 확인.
- 공통 감사 컬럼은 [공통 규칙](../../db-schema/schema-rules.md)을 따르며 사용자 FK를 두지 않습니다.
- 생성 API는 데이터셋과 모든 연결에 동일한 생성·수정 타임스탬프와 작업자를 기록하고 함께 커밋합니다.
- 신규 데이터셋의 연결 1개 이상은 서비스·도메인에서 보장합니다. FK만으로 최소 연결 수가 보장되지는 않습니다. 과거 미귀속 데이터는 연결 없이 남을 수 있으며 일반 목록에서 제외됩니다.

## 이관

V11 DDL → common seed의 V12 DML → V13 DDL 순서입니다. V12는 기존 dataset.workspace_id가 있는 데이터의 공간과 감사 값을 보존합니다. V13은 이관 후 기존 단일 공간 컬럼을 제거합니다. V12 누락을 막기 위해 app DDL과 common seed를 모두 포함한 기존 위치 설정을 유지해야 합니다. 과거 SQL 체크섬·이력은 변경하지 않습니다.

## 조회 범위

로그인 사용자의 ACTIVE 멤버십과 ACTIVE 공간에 연결된 데이터셋만 조회합니다. 여러 연결로 조회된 행은 서비스가 dataset_id로 묶고 최신 생성일 역순·dataset_id 오름차순을 유지합니다. 표시 공간은 접근 가능한 공간만 포함하며, 탈퇴·제거·공간 보관 시 다음 조회에서 제외합니다.
