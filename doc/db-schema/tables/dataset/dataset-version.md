# 데이터셋 버전 테이블 정의

- 종류: 현재 테이블 정의
- 갱신일: 2026-11-23
- 구현: app V27__create_dataset_versions.sql
- 결정: [ADR-052](../../../decisions/ADR-052-dataset-version-snapshots.md)

버전은 현재 활성 이미지·데이터 파일 전체 목록의 고정 스냅샷입니다. 파일 바이트를 복사하지 않습니다. 모든 테이블에 created_at·updated_at(TIMESTAMP(6) WITH TIME ZONE NOT NULL), created_by·updated_by(VARCHAR(100) NOT NULL)를 둡니다. 작업자는 내부 user_key입니다. FK는 삭제 연쇄 없이 참조를 보호합니다.

## dataset_version

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| version_id | VARCHAR(36) PK | 서버 UUID, 고정 버전 ID |
| dataset_id | VARCHAR(36) NOT NULL FK dataset | 원본 데이터셋, 버전 존재 시 물리 삭제 차단 |
| version_number | INTEGER NOT NULL CHECK > 0 | 데이터셋별 1부터 증가 |
| name | VARCHAR(200) NOT NULL | 당시 데이터셋 이름 |
| description | VARCHAR(2000) NULL | 당시 데이터셋 설명 |
| change_note | VARCHAR(2000) NULL | 버전 생성 시 변경 설명 |

UNIQUE: (dataset_id, version_number). 생성 성공 즉시 확정하며 수정·삭제 API는 없습니다. 이미지 수·데이터 파일 수는 연결 테이블에서 조회하므로 중복 컬럼을 두지 않습니다. 빈 데이터셋도 버전을 만들 수 있습니다.

## dataset_version_image / dataset_version_file

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| version_id | VARCHAR(36) NOT NULL FK dataset_version | 소속 버전 |
| image_id 또는 file_id | VARCHAR(36) NOT NULL FK dataset_image 또는 dataset_file | 특정 파일 버전 행 |
| original_filename | VARCHAR(1024) NOT NULL | 당시 원본 상대 이름 |
| description | VARCHAR(2000) NULL | 당시 파일 설명 |
| media_type | VARCHAR(100) NOT NULL | 당시 형식 |
| size_bytes | BIGINT NOT NULL CHECK > 0 | 당시 파일 크기 |
| version_count | INTEGER NOT NULL CHECK > 0 | 개별 파일 버전 번호 |
| source_created_at | TIMESTAMP(6) WITH TIME ZONE NOT NULL | 해당 파일 버전의 등록 시각 |
| source_created_by | VARCHAR(100) NOT NULL | 해당 파일 버전의 등록자 내부 키 |

각 테이블 PK는 (version_id, image_id/file_id)입니다. image_id/file_id에는 참조 보호·검색용 인덱스가 있습니다. 이름·설명 등은 복사하므로 현재 파일 행을 수정해도 과거 메타데이터가 바뀌지 않습니다. 저장 경로는 복사하지 않고 원본 파일 버전 행의 storage_path를 사용합니다. 기존 서비스는 교체 시 새 UUID 파일을 생성하고 이전 바이트를 보존합니다.

현재 is_deleted 여부와 무관하게 스냅샷 구성으로 파일을 읽습니다. 파일 행의 물리 삭제는 FK로 차단하며 버전이 있는 데이터셋 삭제도 서비스의 409 처리와 FK로 막습니다. DB 운영자의 직접 변경까지 불변 트리거로 막는 구현은 추가하지 않았습니다.

## 생성 일관성

기존 파일 추가·교체·논리 삭제·설명 수정과 버전 생성은 같은 dataset 행을 먼저 잠급니다. 한 app 트랜잭션에서 번호 발급·헤더·이미지/파일 스냅샷을 저장하며 실패 시 모두 롤백합니다. 버전 구성에 대한 변경 API나 자동 파일 정리는 없습니다.

## 과거 버전으로 편집본 준비

별도 DDL·base_version_id를 추가하지 않습니다. 기준 구성의 기존 파일 ID를 재활성화하고 스냅샷 설명을 적용하므로 storage_path UNIQUE 제약과 실파일을 그대로 사용합니다. 과거 확정 스냅샷의 헤더/구성 행은 수정하지 않습니다. 정확한 파일 버전 식별은 UUID이며, 되돌린 파일에서 새로 교체하면 과거 다른 분기와 개별 version_count가 같을 수 있습니다. 데이터셋 전체 버전 번호의 고유성·증가는 유지합니다. [ADR-053](../../../decisions/ADR-053-edit-from-dataset-version.md)을 따릅니다.
