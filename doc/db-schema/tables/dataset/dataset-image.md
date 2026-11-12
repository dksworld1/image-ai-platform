# dataset_image 테이블 정의

- 상태: app V18·이미지 업로드·메타데이터 목록 구현, 실제 DB 미적용
- 기준일: 2026-11-12
- 데이터소스: app
- 마이그레이션: `db/migration/app/V18__create_dataset_images.sql`
- 결정: [ADR-036](../../../decisions/ADR-036-dataset-image-upload.md)

## 목적과 컬럼

이미지 원본 바이트는 로컬 파일로 저장하고, 사용자에게 표시할 원본명과 파일 위치·형식·크기를 DB에 저장합니다.

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| image_id | VARCHAR(36) | 불가 | 없음 | 서버 UUID, PK |
| dataset_id | VARCHAR(36) | 불가 | 없음 | 귀속 데이터셋 FK |
| original_filename | VARCHAR(255) | 불가 | 없음 | 경로를 제외한 원본 파일명 |
| storage_path | VARCHAR(100) | 불가 | 없음 | 저장 루트 기준 상대 파일 경로, UUID.검증된확장자 |
| media_type | VARCHAR(100) | 불가 | 없음 | 콘텐츠로 판정한 image/png·jpeg·gif·bmp |
| size_bytes | BIGINT | 불가 | 없음 | 원본 바이트 크기, Java Long |
| created_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 수정일시, 생성 시 동일 |
| created_by | VARCHAR(100) | 불가 | '0' | 서버 인증의 내부 user_key |
| updated_by | VARCHAR(100) | 불가 | '0' | 생성 시 created_by와 동일 |

공통 컬럼은 [공통 스키마 규칙](../../schema-rules.md)을 따릅니다. API 업로드는 작업자·시각을 명시하며 로그인 ID·실명을 작업자로 저장하지 않습니다.

## 제약과 관계

- `pk_dataset_image`: image_id PK.
- `uq_dataset_image_filename`: (dataset_id, original_filename) UNIQUE. 동일 데이터셋 내에서 대소문자를 구분한 원본명 중복을 거부합니다. 다른 데이터셋에는 같은 이름을 허용합니다.
- `uq_dataset_image_storage`: storage_path UNIQUE. 서로 다른 이미지가 같은 파일 경로를 사용하지 않습니다.
- `fk_dataset_image_dataset`: dataset FK, ON DELETE CASCADE·ON UPDATE NO ACTION. 데이터셋 삭제는 메타데이터만 함께 삭제하며 파일 삭제는 수행하지 않습니다.
- `ck_dataset_image_size`: size_bytes > 0. 최대 파일 크기와 실제 이미지 형식·화소는 서비스 및 multipart 설정에서 확인합니다.
- 원본명은 검색 가능한 표시 값이며 파일 경로나 식별자로 사용하지 않습니다. basename 외에는 임의 공백 제거·소문자화·이름 변경을 하지 않습니다.

등록 목록은 권한 있는 데이터셋 안에서 created_at DESC·image_id ASC로 조회합니다. 현재 응답에는 storage_path와 실제 로컬 루트 경로를 노출하지 않습니다.
