# 아티팩트셋 폐기 이력

- 상태: 폐기
- 기준일: 2026-11-21
- 관련 결정: [ADR-049](../../decisions/ADR-049-artifact-file-unification.md)

## 이전 API

### 아티팩트셋·버전

| 메서드·경로 | pipeline:artifactset 권한 동작 | 결과 |
|---|---|---|
| POST /api/artifact-sets | create | 논리 셋 생성, 201 |
| GET /api/artifact-sets?workspaceId=... | read | 공간의 셋 배열, 200 |
| GET /api/artifact-sets/{id} | read | 셋 메타데이터, 200 |
| POST /api/artifact-sets/{id}/versions | update | 비어 있는 DRAFT 버전 생성, 201 |
| GET /api/artifact-sets/{id}/versions | read | 버전 번호순 배열, 200 |
| GET /api/artifact-sets/{id}/versions/{versionId} | read | 버전 메타데이터, 200 |
| GET /api/artifact-sets/{id}/versions/{versionId}/members | read | ordinal 순 구성원 배열, 200 |
| PUT /api/artifact-sets/{id}/versions/{versionId}/members | update + pipeline:artifact:read | 초안 구성 전체 교체, 200 |
| POST /api/artifact-sets/{id}/versions/{versionId}/confirm | update | CONFIRMED 확정, 200. 반복 호출은 같은 결과 |

셋 생성 요청:

```json
{"workspaceId":"공간 UUID","name":"학습 입력","purpose":"TRAINING","description":null}
```

name(200자)·purpose(100자)는 필수이며 description은 선택·2000자입니다. 셋 생성과 첫 버전 생성은 별도 요청입니다. 버전 번호는 서버가 셋별로 1부터 발급합니다. 새 버전은 항상 빈 초안이며 기존 버전을 자동 복제하지 않습니다.

구성 전체 교체 요청:

```json
{"members":[{"artifactId":"아티팩트 UUID","ordinal":0,"sourceKey":"원본 이미지 ID 또는 대응 키"}]}
```

members는 필수이며 []로 비울 수 있습니다. 구성원은 같은 공간의 AVAILABLE 아티팩트여야 합니다. artifactId·0 이상 ordinal은 버전 안에서 각각 고유하며 sourceKey는 선택·1024자입니다. sourceKey 중복과 순서의 공백은 허용합니다. 잘못된 항목이 하나라도 있으면 기존 구성 전체를 유지합니다. 구성은 참조이므로 파일을 다시 복사하지 않습니다.

확정 시 itemCount·compositionChecksum·confirmedAt을 기록하며 구성·순서를 바꿀 수 없습니다. 빈 셋도 확정 가능합니다. 수정은 새 버전을 생성하여 구성합니다. itemCount는 아티팩트 개수이며 파일 개수·내부 행 개수가 아닙니다. DRAFT의 itemCount·compositionChecksum·confirmedAt은 null입니다.

구성 체크섬은 ordinal 오름차순 항목에 대해 Java DataOutputStream으로 writeUTF("artifact-set-v1"), writeInt(항목 수), 각 항목 writeLong(ordinal)·writeUTF(artifactId)·writeBoolean(sourceKey 존재 여부)·선택 writeUTF(sourceKey)를 직렬화한 바이트의 SHA-256 소문자 hex입니다. 셋·버전 ID를 포함하지 않아 같은 구성·순서·대응 키는 같은 체크섬입니다.

셋 응답: setId, workspaceId, name, purpose, description 및 공통 감사 필드. 버전 응답: versionId, setId, versionNumber, status, itemCount, compositionChecksum, confirmedAt 및 공통 감사 필드. 항목 응답: versionId, artifactId, ordinal, sourceKey 및 공통 감사 필드. 공통 감사 필드는 createdAt, updatedAt(ISO 8601), createdBy, updatedBy(내부 user_key)입니다.

## 이전 테이블 정의

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

## 아티팩트·아티팩트셋 백엔드 구현 — 2026-11-20

사용자의 구현 지시로 4·5절의 아티팩트/파일과 셋/버전/구성을 채택했습니다. 이 기록은 전체 파이프라인 설계의 구현 승인이나 완료를 뜻하지 않습니다. 물리 결정은 [ADR-047](../../decisions/ADR-047-artifacts-and-versioned-sets.md), [테이블 정의](../../db-schema/tables/artifact/README.md), 당시 아티팩트 HTTP 계약은 [아티팩트 REST API 이력](../artifact-rest-api/README.md)에 보존합니다.

### 목표·원리·완료 조건

- 아티팩트 한 ID가 파일 여러 개의 고정 내용을 표현합니다. WRITING에서 기록하고 실제 파일의 크기·SHA-256 검증 후 AVAILABLE로 확정합니다. 파일 교체는 새 아티팩트로 표현합니다.
- 셋은 논리 ID와 증가하는 버전을 분리하고 초안 구성을 편집·참조 재사용합니다. 같은 공간의 AVAILABLE 아티팩트만 포함하고 빈 셋도 확정할 수 있습니다.
- CONFIRMED 버전의 구성·순서·대응 키는 변경하지 않습니다. 구성 체크섬과 아티팩트 개수·확정 시각을 기록합니다.
- 생성·조회·파일 추가/다운로드·완료/실패, 버전 생성·구성 전체 교체·확정 API와 별도 artifact/ artifactset 기능 권한을 제공합니다.
- 버전 번호 발급은 셋 행, 초안 구성 변경/확정은 버전 행, 파일 추가/확정은 아티팩트 행 잠금으로 경쟁을 제어합니다. DB 롤백 시 새 파일 삭제를 보상합니다.

### 이번에 포함하지 않은 범위

실행 엔진·입출력 바인딩·실행 시도·할당 토큰·스케줄러·프로비넌스·원본 데이터셋 가져오기·프런트 화면은 후속입니다. 현재 완료 API는 개별 아티팩트/셋의 확정이며 실행 시도의 모든 출력을 일괄 채택하는 기능은 아닙니다.

보관 기한·보호 여부는 메타데이터로 기록만 하고 삭제·보호 해제·실체 정리 API는 제공하지 않습니다. 원본 데이터셋 참조 보호와 삭제 정책은 기존 미정 사항으로 유지합니다. NAS·S3는 도입하지 않았으며 현재 파일 저장은 설정된 로컬 루트 키에 UUID 경로로 기록합니다.

### 검증

Java 21에서 ArtifactTest 7개와 기존 DatasetFileTest 4개, 총 11개 선별 테스트 및 Maven package가 통과했습니다. 다중 파일·체크섬 검증·완료 불변성·확정 셋 재사용/빈 셋·공간/권한 제한·구성 교체 롤백·실파일 불일치·I/O 실패 보상·동시 버전 할당과 업로드/완료 경쟁을 확인했습니다. 전체 테스트는 실행하지 않았습니다.

기존 local DB 파일이 없는 상태에서 Linux용 기존 start-local-backend.sh로 local 프로파일을 기동해 새 DB에 V26까지 적용했습니다. 이 환경에는 PowerShell이 없어 Windows ps1 대신 기존 sh 스크립트를 사용했습니다. 8080의 GET /api/health UP 및 admin 인증 후 GET /api/artifacts·/api/artifact-sets의 200을 확인했습니다. 실제 local DB에는 검증용 아티팩트·셋을 생성하지 않았습니다. 외부 NAS·운영 DB에는 적용하지 않았습니다.
