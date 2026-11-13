# dataset_image 테이블 정의

- 상태: V18~22·일반/수정 업로드·활성 이미지 조회·설명 편집 구현, 실제 DB 미적용
- 기준일: 2026-11-13
- 데이터소스: app
- 마이그레이션: app V18·V19·V20·V22, 공통 DML seed V21
- 결정: [ADR-036](../../../decisions/ADR-036-dataset-image-upload.md), [ADR-039](../../../decisions/ADR-039-image-version-history.md)

## 목적과 컬럼

이미지 원본 바이트는 로컬 파일로 저장하고, 사용자에게 표시할 원본명과 파일 위치·형식·크기를 DB에 저장합니다.

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| image_id | VARCHAR(36) | 불가 | 없음 | 서버 UUID, PK |
| dataset_id | VARCHAR(36) | 불가 | 없음 | 귀속 데이터셋 FK |
| original_filename | VARCHAR(1024) | 불가 | 없음 | 선택 폴더부터의 원본 상대 경로 또는 단일 파일명 |
| description | VARCHAR(2000) | 허용 | NULL | V22 추가, 버전별 이미지 설명. 공백만 있으면 NULL |
| storage_path | VARCHAR(100) | 불가 | 없음 | 저장 루트 기준 상대 파일 경로, UUID.검증된확장자 |
| thumbnail_path | VARCHAR(100) | 허용 | NULL | V19 추가, UUID-thumbnail.png 상대 경로. 기존 미생성 이미지는 NULL |
| media_type | VARCHAR(100) | 불가 | 없음 | 콘텐츠로 판정한 image/png·jpeg·gif·bmp |
| size_bytes | BIGINT | 불가 | 없음 | 원본 바이트 크기, Java Long |
| is_deleted | BOOLEAN | 불가 | FALSE | 교체된 이전 버전의 삭제 표시 |
| version_count | INTEGER | 불가 | 1 | 원본명 계열의 버전 번호, 교체마다 +1 |
| previous_image_id | VARCHAR(36) | 허용 | NULL | 교체 전 이미지 ID, 최초 NULL |
| active_filename | VARCHAR(1024) | 허용 | 계산값 | is_deleted=false이면 original_filename, 아니면 NULL. 애플리케이션 직접 입력 없음 |
| created_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 수정일시, 생성 시 동일 |
| created_by | VARCHAR(100) | 불가 | '0' | 서버 인증의 내부 user_key |
| updated_by | VARCHAR(100) | 불가 | '0' | 생성 시 created_by와 동일 |

공통 컬럼은 [공통 스키마 규칙](../../schema-rules.md)을 따릅니다. API 업로드는 작업자·시각을 명시하며 로그인 ID·실명을 작업자로 저장하지 않습니다.

## 제약과 관계

- `pk_dataset_image`: image_id PK.
- V20은 `uq_dataset_image_filename`을 제거하고 `uq_dataset_image_active_filename` (dataset_id, active_filename) UNIQUE로 대체합니다. 활성 이름만 대소문자를 구분해 중복 거부하며 삭제 표시 버전은 여러 행을 보존합니다.
- `uq_dataset_image_storage`: storage_path UNIQUE. 서로 다른 이미지가 같은 파일 경로를 사용하지 않습니다.
- `uq_dataset_image_thumbnail`: thumbnail_path UNIQUE. V19는 기존 데이터를 보존하며 썸네일을 소급 생성하지 않습니다. 신규 업로드는 원본과 썸네일을 모두 저장해야 성공합니다. [ADR-038](../../../decisions/ADR-038-image-thumbnails.md)을 따릅니다.
- `fk_dataset_image_dataset`: dataset FK, ON DELETE CASCADE·ON UPDATE NO ACTION. 데이터셋 삭제는 메타데이터만 함께 삭제하며 파일 삭제는 수행하지 않습니다.
- `ck_dataset_image_size`: size_bytes > 0. 최대 파일 크기와 실제 이미지 형식·화소는 서비스 및 multipart 설정에서 확인합니다.
- `ck_dataset_image_version`: version_count > 0. 삭제 표시 전환 시 기존 created_at·created_by는 보존하고 updated_at·updated_by만 변경합니다. 새 버전 행은 새 UUID·신규 등록 감사 값을 가지며 이전 원본과 썸네일을 유지합니다.
- 원본명은 검색 가능한 표시 값이며 파일 경로나 식별자로 사용하지 않습니다. basename 외에는 임의 공백 제거·소문자화·이름 변경을 하지 않습니다.

등록 목록·개수·원본/썸네일 조회는 is_deleted=false만 대상으로 합니다. 목록은 created_at DESC·image_id ASC이며 응답에 실제 저장 경로를 노출하지 않습니다. 등록자·수정자 표시는 created_by·updated_by와 auth_user를 LEFT JOIN한 현재 로그인 ID·실명입니다. 버전 행의 생성자는 해당 파일을 등록한 사용자이며 최초 계열의 등록자는 이전 버전과 [이력](dataset-image-history.md)에서 확인합니다. FK CASCADE로 데이터셋의 버전 행이 삭제되어도 이력 스냅샷은 보존합니다.

2026-11-13 최초 등록일시: 조회 응답의 firstRegisteredAt은 별도 저장 컬럼이 아닙니다. dataset_id로 한정한 재귀 조회에서 버전 1·previous_image_id=NULL인 시작 행의 created_at을 같은 연결의 후속 버전에 전달합니다. 삭제 표시된 이전 행도 연결 계산에는 포함하지만 최종 목록은 활성 행만 반환합니다. 파일명 기준 MIN 집계가 아니므로 동명 재등록 계열과 섞이지 않습니다. 시작 버전을 찾을 수 없으면 NULL입니다.

2026-11-13 설명 편집: description은 파일 업로드 시 저장하거나 활성 행에서 수정할 수 있습니다. 설명만 수정하면 updated_at·updated_by와 UPDATE 이력만 갱신하고 version_count·생성 감사 정보·파일은 유지합니다. 일괄 저장은 데이터셋 잠금과 한 트랜잭션으로 처리합니다. 기존 행은 V22에서 NULL을 유지하며 별도 DML은 없습니다. [ADR-042](../../../decisions/ADR-042-image-descriptions.md)를 따릅니다.

2026-11-13: 개별 이미지 삭제도 is_deleted=true와 수정 감사 값만 갱신합니다. 생성 감사 정보·버전·원본/썸네일 경로·실제 파일을 보존하며 DELETE 이력과 같은 트랜잭션으로 처리합니다. 새 DDL은 없습니다. [ADR-041](../../../decisions/ADR-041-image-logical-deletion.md)을 따릅니다.


V25·ADR-046: 원본명과 활성 중복 키에 전체 상대 경로를 보존합니다. 각 요소 최대 255자, 전체 최대 1,024자입니다. 다른 하위 폴더의 동일 파일명은 별개이며 동일 경로만 중복·교체 대상으로 처리합니다. 이력에도 상대 경로를 그대로 기록합니다. 기존 파일명과 디스크 경로는 변경하지 않습니다.
