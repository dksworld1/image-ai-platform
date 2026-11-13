# dataset_image 테이블 정의

- 상태: V18~20·일반/수정 업로드·활성 이미지 조회 구현, 실제 DB 미적용
- 기준일: 2026-11-13
- 데이터소스: app
- 마이그레이션: app V18·V19·V20, 공통 DML seed V21
- 결정: [ADR-036](../../../decisions/ADR-036-dataset-image-upload.md), [ADR-039](../../../decisions/ADR-039-image-version-history.md)

## 목적과 컬럼

이미지 원본 바이트는 로컬 파일로 저장하고, 사용자에게 표시할 원본명과 파일 위치·형식·크기를 DB에 저장합니다.

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| image_id | VARCHAR(36) | 불가 | 없음 | 서버 UUID, PK |
| dataset_id | VARCHAR(36) | 불가 | 없음 | 귀속 데이터셋 FK |
| original_filename | VARCHAR(255) | 불가 | 없음 | 경로를 제외한 원본 파일명 |
| storage_path | VARCHAR(100) | 불가 | 없음 | 저장 루트 기준 상대 파일 경로, UUID.검증된확장자 |
| thumbnail_path | VARCHAR(100) | 허용 | NULL | V19 추가, UUID-thumbnail.png 상대 경로. 기존 미생성 이미지는 NULL |
| media_type | VARCHAR(100) | 불가 | 없음 | 콘텐츠로 판정한 image/png·jpeg·gif·bmp |
| size_bytes | BIGINT | 불가 | 없음 | 원본 바이트 크기, Java Long |
| is_deleted | BOOLEAN | 불가 | FALSE | 교체된 이전 버전의 삭제 표시 |
| version_count | INTEGER | 불가 | 1 | 원본명 계열의 버전 번호, 교체마다 +1 |
| previous_image_id | VARCHAR(36) | 허용 | NULL | 교체 전 이미지 ID, 최초 NULL |
| active_filename | VARCHAR(255) | 허용 | 계산값 | is_deleted=false이면 original_filename, 아니면 NULL. 애플리케이션 직접 입력 없음 |
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
