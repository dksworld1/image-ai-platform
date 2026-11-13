# 데이터 파일과 작업 이력

- 종류: 현재 테이블 정의
- 상태: 승인·구현 완료 / 로컬 DB 적용은 운영 기록 참조
- 기준일: 2026-11-13
- 구조 SQL: V23__create_dataset_files.sql, V24__expand_dataset_file_storage_paths.sql

## dataset_file

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| file_id | VARCHAR(36), PK | 서버 UUID, 파일 버전 식별자 |
| dataset_id | VARCHAR(36), NOT NULL, FK CASCADE | 소속 데이터셋 |
| original_filename | VARCHAR(1024), NOT NULL | 선택 폴더부터의 원본 상대 경로 또는 단일 파일명 |
| storage_path | VARCHAR(300), NOT NULL, UNIQUE | 저장 루트 아래 UUID + 원본 확장자 상대 경로. 기존 UUID.bin 유지 |
| media_type | VARCHAR(100), NOT NULL | application/octet-stream |
| size_bytes | BIGINT, NOT NULL, >0 | 원본 바이트 수 |
| is_deleted | BOOLEAN, NOT NULL, 기본 FALSE | 삭제/교체 표시 |
| version_count | INTEGER, NOT NULL, 기본 1, >0 | 교체 시 증가 |
| previous_file_id | VARCHAR(36), NULL 허용 | 이전 버전 ID, FK 없음 |
| description | VARCHAR(2000), NULL 허용 | 파일별 설명 |
| active_filename | VARCHAR(1024), 생성 컬럼 | 활성 원본명, 삭제 행은 NULL |
| created_at / updated_at | TIMESTAMP(6) WITH TIME ZONE, NOT NULL | 생성/최종 수정 시각 |
| created_by / updated_by | VARCHAR(100), NOT NULL | 내부 user_key 또는 시스템 '0', FK 없음 |

(dataset_id, active_filename) UNIQUE로 데이터셋 내 활성 원본명 중복을 방지합니다. 교체는 기존 행에 삭제 표시하고 새 버전 행을 추가합니다. 최초 등록 시각은 previous_file_id 계보에서 계산하며 별도 저장하지 않습니다. 설명 수정은 버전을 올리지 않습니다.

## dataset_file_history

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| history_id | VARCHAR(36), PK | 이벤트 UUID |
| file_id / dataset_id | VARCHAR(36), NOT NULL | 대상 버전·데이터셋, FK 없음 |
| action | VARCHAR(10), NOT NULL, CHECK | CREATE / UPDATE / DELETE / BASELINE |
| version_count | INTEGER, NOT NULL, >0 | 대상 버전 |
| previous_file_id | VARCHAR(36), NULL 허용 | 이전 버전 연결 |
| original_filename | VARCHAR(1024), NOT NULL | 원본명 스냅샷 |
| storage_path | VARCHAR(300), NOT NULL | 파일 경로 스냅샷 |
| description | VARCHAR(2000), NULL 허용 | 설명 스냅샷 |
| media_type | VARCHAR(100), NOT NULL | 형식 스냅샷 |
| size_bytes | BIGINT, NOT NULL | 크기 스냅샷 |
| file_created_at / file_updated_at | TIMESTAMP(6) WITH TIME ZONE, NOT NULL | 대상 파일 감사 시각 스냅샷 |
| file_created_by / file_updated_by | VARCHAR(100), NOT NULL | 대상 파일 감사 작업자 스냅샷 |
| created_at / updated_at | TIMESTAMP(6) WITH TIME ZONE, NOT NULL | 이벤트 감사 시각 |
| created_by / updated_by | VARCHAR(100), NOT NULL | 이벤트 작업자, FK 없음 |

인덱스는 (file_id, created_at), (dataset_id, original_filename, version_count)입니다. 메타데이터 변경과 이력은 같은 트랜잭션입니다. 단일 파일 삭제는 행·실제 파일을 보존합니다. 데이터셋 삭제는 활성 파일의 DELETE 이력을 먼저 기록하고 FK로 버전 행을 제거하며 실제 파일·이력은 보존합니다. 이력 테이블에 상위 FK를 두지 않습니다. BASELINE은 허용 값이며 현재 신규 파일 흐름은 CREATE/UPDATE/DELETE를 기록합니다.

상세 동작은 [API](../../../api/api.md#데이터-파일-api--2026-11-13), 공통 컬럼은 [생성 규칙](../../schema-rules.md)을 따릅니다.


V25·ADR-046: 원본명과 활성 중복 키에 전체 상대 경로를 보존합니다. 각 요소 최대 255자, 전체 최대 1,024자입니다. 다른 하위 폴더의 동일 파일명은 별개이며 동일 경로만 중복·교체 대상으로 처리합니다. 이력에도 상대 경로를 그대로 기록합니다. 기존 파일명과 디스크 경로는 변경하지 않습니다.
