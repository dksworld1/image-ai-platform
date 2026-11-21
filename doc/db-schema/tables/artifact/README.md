# 아티팩트·아티팩트셋 테이블 정의

- 종류: 현재 테이블 정의
- 갱신일: 2026-11-20
- 구현: app DB의 V26, [ADR-047](../../../decisions/ADR-047-artifacts-and-versioned-sets.md)

모든 테이블에 created_at·updated_at(TIMESTAMP(6) WITH TIME ZONE NOT NULL), created_by·updated_by(VARCHAR(100) NOT NULL)를 둡니다. 작업자는 내부 user_key이며 시스템 작업자는 '0'입니다. 아래 표 외에 이 공통 컬럼이 포함됩니다. 모든 FK는 부모 삭제 연쇄 없이 참조를 보존합니다.

## artifact

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| artifact_id | VARCHAR(36) PK | 서버 UUID, 내용의 고정 버전 |
| workspace_id | VARCHAR(36) NOT NULL FK workspace | 귀속 공간 |
| data_type | VARCHAR(100) NOT NULL | 데이터 종류, 플랫폼 열거형 강제 없음 |
| schema_name | VARCHAR(200) NULL | 선택 스키마 이름 |
| schema_version | VARCHAR(100) NULL | 선택 스키마 버전, 이름과 함께 지정 |
| status | VARCHAR(20) NOT NULL CHECK | WRITING·AVAILABLE·FAILED |
| description | VARCHAR(2000) NULL | 설명 |
| retention_until | TIMESTAMP(6) WITH TIME ZONE NULL | 보관 검토 시점 |
| protected_flag | BOOLEAN NOT NULL | 보호 메타데이터, 기본 false를 서비스에서 명시 |

조회 인덱스: (workspace_id, created_at). WRITING→AVAILABLE 또는 FAILED만 제공합니다. AVAILABLE·FAILED에서는 파일 변경·상태 되돌리기를 금지합니다.

## artifact_file

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| file_id | VARCHAR(36) PK | 서버 UUID |
| artifact_id | VARCHAR(36) NOT NULL FK artifact | 소속 아티팩트 |
| internal_name | VARCHAR(1024) NOT NULL | 아티팩트 내 논리 상대 이름 |
| root_key | VARCHAR(100) NOT NULL | 서버 설정의 저장 루트 키 |
| relative_path | VARCHAR(200) NOT NULL | artifact UUID / file UUID |
| media_type | VARCHAR(200) NOT NULL | 업로더가 선언한 MIME 형식 |
| size_bytes | BIGINT NOT NULL CHECK >= 0 | 실측 바이트 수, 빈 파일 허용 |
| checksum_algorithm | VARCHAR(20) NOT NULL CHECK SHA-256 | 체크섬 방식 |
| checksum | VARCHAR(64) NOT NULL | 소문자 SHA-256 |
| deleted_at | TIMESTAMP(6) WITH TIME ZONE NULL | 실체 삭제 시각 예약 필드, 현재 NULL |

UNIQUE: (artifact_id, internal_name), (root_key, relative_path). 업로드가 성공한 파일 행만 저장하므로 기록 중 아티팩트라도 파일 행의 크기·체크섬은 확정값입니다. 실패한 업로드는 행을 남기지 않습니다. 절대 경로를 저장하지 않습니다.

## artifact_set

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| set_id | VARCHAR(36) PK | 논리적 셋 식별자 |
| workspace_id | VARCHAR(36) NOT NULL FK workspace | 귀속 공간 |
| name | VARCHAR(200) NOT NULL | 표시 이름 |
| purpose | VARCHAR(100) NOT NULL | 목적, 플랫폼 열거형 강제 없음 |
| description | VARCHAR(2000) NULL | 설명 |

조회 인덱스: (workspace_id, created_at). 생성 시 버전을 자동 생성하지 않습니다.

## artifact_set_version

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| version_id | VARCHAR(36) PK | 고정 버전 참조 ID |
| set_id | VARCHAR(36) NOT NULL FK artifact_set | 소속 셋 |
| version_number | INTEGER NOT NULL CHECK > 0 | 셋 안에서 1부터 증가 |
| status | VARCHAR(20) NOT NULL CHECK | DRAFT·CONFIRMED |
| item_count | BIGINT NULL CHECK >= 0 | 확정 시 아티팩트 개수, 초안에서는 NULL |
| composition_checksum | VARCHAR(64) NULL | 확정 구성의 SHA-256 |
| confirmed_at | TIMESTAMP(6) WITH TIME ZONE NULL | 확정 시각 |

UNIQUE: (set_id, version_number). CONFIRMED에서는 개수·체크섬·확정 시각이 모두 필수입니다. 빈 셋의 item_count는 0입니다. 파일 수·벡터 행 수와 구분합니다.

## artifact_set_member

| 컬럼 | 타입·제약 | 의미 |
|---|---|---|
| version_id | VARCHAR(36) NOT NULL FK artifact_set_version | 소속 버전 |
| artifact_id | VARCHAR(36) NOT NULL FK artifact | 참조 아티팩트 |
| ordinal | BIGINT NOT NULL CHECK >= 0 | 순서, 연속일 필요 없음 |
| source_key | VARCHAR(1024) NULL | 원본 대응 키 |

PK: (version_id, artifact_id), UNIQUE: (version_id, ordinal). source_key 중복은 허용합니다. 셋 중첩은 없습니다. 서비스가 같은 공간·AVAILABLE 상태를 검사합니다. 구성 교체·확정은 버전 행 잠금 안에서 수행하고, 확정 후 변경 API를 거부합니다.
