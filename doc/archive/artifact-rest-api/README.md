# 아티팩트 REST API 이력

- 상태: 비노출
- 기준일: 2026-11-21
- 관련 결정: [ADR-051](../../decisions/ADR-051-in-process-artifact-lifecycle.md)

## 아티팩트와 파일

2026-11-20 구현. 활성 계정의 Bearer JWT와 해당 기능 권한, ACTIVE 공간의 ACTIVE 멤버십이 모두 필요합니다. 관리자·생성자 우회는 없습니다. 작업자는 인증 컨텍스트의 내부 user_key로 기록합니다. 기존 dataset:* 권한은 자동으로 대체하지 않습니다.

### 아티팩트

| 메서드·경로 | pipeline:artifact 권한 동작 | 결과 |
|---|---|---|
| POST /api/artifacts | create | WRITING 아티팩트 생성, 201 |
| GET /api/artifacts?workspaceId=... | read | 해당 공간 아티팩트 배열, 200 |
| GET /api/artifacts/{id} | read | 아티팩트 메타데이터, 200 |
| POST /api/artifacts/{id}/files | update | multipart file 1개, 선택 internalName, 201 |
| GET /api/artifacts/{id}/files | read | 내부 파일 메타데이터 배열, 200 |
| GET /api/artifacts/{id}/files/{fileId}/content | read | AVAILABLE만 원본 다운로드, attachment·no-store·application/octet-stream |
| POST /api/artifacts/{id}/complete | update | 크기·체크섬 재검증 후 AVAILABLE, 200. 완료 반복은 같은 결과 |
| POST /api/artifacts/{id}/fail | update | WRITING→FAILED, 200. 실패 반복은 같은 결과 |

생성 요청 예:

```json
{"workspaceId":"공간 UUID","dataType":"MODEL","schemaName":"model-weights","schemaVersion":"1","description":"가중치와 설정","retentionUntil":null,"protectedFlag":false}
```

workspaceId(최대 36자)·dataType(최대 100자)는 필수입니다. schemaName(200자)·schemaVersion(100자)는 함께 지정하거나 생략합니다. description은 2000자, retentionUntil은 ISO 8601 시각 또는 null이며 protectedFlag 생략·null은 false입니다. 종류·스키마 실체 등록과 스키마 내용 검증은 하지 않습니다.

한 아티팩트에 여러 파일을 추가할 수 있습니다. internalName은 기본 원본 파일명이며 최대 1024자 논리 상대 경로입니다. 빈 구간·절대 경로·`.`·`..`·콜론·제어문자와 255자를 넘는 경로 구간을 거부합니다. 같은 내부 이름은 409이며 덮어쓰지 않습니다. 기본 한도는 파일당 10MB이며 빈 파일은 허용합니다. 확장자 제한은 없습니다. 서버가 실측 크기·SHA-256을 기록합니다. 파일 내용은 내부 이름 대신 서버 UUID 경로에 저장합니다.

완료에는 적어도 파일 하나가 필요하고 모든 파일의 실재·크기·체크섬을 다시 확인합니다. AVAILABLE과 FAILED는 파일을 추가하거나 다른 상태로 바꿀 수 없습니다. WRITING·FAILED는 메타데이터 조회만 가능하며 다운로드·셋 구성원 등록은 금지합니다.

아티팩트 응답: artifactId, workspaceId, dataType, schemaName, schemaVersion, status, description, retentionUntil, protectedFlag 및 공통 감사 필드. 파일 응답: fileId, artifactId, internalName, rootKey, relativePath, mediaType, sizeBytes, checksumAlgorithm, checksum, deletedAt 및 공통 감사 필드. rootKey·relativePath는 논리 위치이며 실제 절대 경로는 반환하지 않습니다. mediaType은 선언 정보이며 다운로드는 실행 가능한 HTML 등으로 렌더링하지 않고 바이너리 첨부로 제공합니다.

공통 감사 필드는 createdAt, updatedAt(ISO 8601), createdBy, updatedBy(내부 user_key)입니다.

### 오류와 범위

인증 오류 401, 기능 권한 없음·비멤버·비활성 공간·없는 아티팩트 403, 접근 가능한 아티팩트 내 없는 파일 404, 잘못된 입력 400, 중복 내부 파일명·상태 위반·빈 아티팩트 완료·파일 검증 실패 409입니다. 전역 multipart 한도 초과는 413, 그 밖의 DB·스토리지 장애는 5xx입니다.

목록 검색·페이지 처리, 보호/보관 정책 변경, 삭제·실체 정리·NAS/S3, 기존 데이터셋 가져오기, 실행·포트·프로비넌스는 이번 API에 포함하지 않습니다. 보관 기한·보호 여부는 기록만 하며 자동 삭제하지 않습니다. 확정 아티팩트를 다음 단계에 전달하는 실행 모듈은 후속입니다.
