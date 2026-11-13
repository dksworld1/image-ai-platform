# dataset_image_history 테이블 정의

- 종류: 현재 데이터 계약
- 상태: V20 DDL·공통 V21 이관·서비스 이벤트 저장 구현, 실제 DB 미적용
- 기준일: 2026-11-13
- 데이터소스: app
- 결정: [ADR-039](../../../decisions/ADR-039-image-version-history.md)

## 목적과 컬럼

버전별 이미지 생성·수정 업로드·삭제의 작업자와 시각을 보존하는 추가 전용 이력입니다. 부모 삭제 후에도 조회 근거가 남도록 dataset·image·작업자 FK를 두지 않습니다.

| 컬럼 | 형식 | NULL | 설명 |
|---|---|---|---|
| history_id | VARCHAR(36) | 불가 | 서버 UUID PK. V21 기존 행 이관은 기존 image_id 사용 |
| image_id | VARCHAR(36) | 불가 | 이벤트 대상 버전 이미지 ID |
| dataset_id | VARCHAR(36) | 불가 | 원래 데이터셋 ID |
| action | VARCHAR(10) | 불가 | CREATE·UPDATE·DELETE·BASELINE CHECK |
| version_count | INTEGER | 불가 | 해당 버전 번호, 양수 CHECK |
| previous_image_id | VARCHAR(36) | 허용 | 교체 전 버전 ID |
| original_filename | VARCHAR(255) | 불가 | 원본명 스냅샷 |
| storage_path | VARCHAR(100) | 불가 | 해당 버전 원본 상대 경로 |
| thumbnail_path | VARCHAR(100) | 허용 | 해당 버전 썸네일 상대 경로 |
| media_type | VARCHAR(100) | 불가 | 원본 콘텐츠 형식 |
| size_bytes | BIGINT | 불가 | 원본 크기 |
| image_created_at·image_updated_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | 이벤트 당시 이미지 행의 생성·수정 시각 |
| image_created_by·image_updated_by | VARCHAR(100) | 불가 | 이벤트 당시 이미지 행의 생성·수정 user_key |
| created_at·updated_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | 이력 이벤트 기록 시각, 기본 CURRENT_TIMESTAMP. 생성 시 동일 |
| created_by·updated_by | VARCHAR(100) | 불가 | 이벤트 실행 user_key, 기본 '0'. 생성 시 동일 |

공통 감사 컬럼은 [스키마 규칙](../../schema-rules.md)을 따릅니다. 서비스는 시각과 작업자를 명시합니다. 이력은 이후 수정하지 않습니다. 사용자 이름은 스냅샷하지 않으며 영구 내부 user_key가 추적 기준입니다.

## 이벤트와 적용 순서

- 신규 파일: CREATE 1건.
- 수정 업로드의 일치 파일: 기존 버전 삭제 표시와 DELETE 1건, 새 버전 생성과 UPDATE 1건. previous_image_id와 version_count로 연결합니다.
- 수정 업로드의 불일치 파일: 신규 CREATE, 버전 1.
- 데이터셋 삭제: 활성 이미지 각각 DELETE 스냅샷 후 기존 FK CASCADE. 이력·실제 파일은 보존합니다.
- 기존 V19 이미지: 공통 seed V21에서 BASELINE. 이미지의 과거 감사 값은 image_* 필드에 보존하고 이벤트 기록자는 시스템 '0'입니다. 과거 CREATE·UPDATE를 추정해 만들지 않습니다.

이벤트는 원래 작업과 같은 app 트랜잭션으로 저장합니다. 실패 시 삭제 표시·새 버전·이력 모두 롤백합니다. 직접 SQL 변경의 자동 추적 트리거와 이력 API·화면은 없습니다.

인덱스는 `(image_id, created_at)`, `(dataset_id, original_filename, version_count)`입니다. 동시 버전 변경은 데이터셋 행 잠금과 활성 이름 UNIQUE로 제어합니다.
