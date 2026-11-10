# dataset 테이블 정의

- 상태: 생성 구현
- 갱신일: 2026-11-10
- 데이터소스: app
- 마이그레이션: `backend/src/main/resources/db/migration/app/V6__create_dataset.sql`
- 대응 모델: `com.imageaiplatform.dataset.domain.Dataset`

## 목적

이름·설명으로 생성한 데이터셋을 저장합니다. 이름은 식별자가 아니며 중복을 허용합니다. 별도 소유자·공유 범위는 현재 정의하지 않습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---|---|---|
| dataset_id | VARCHAR(36) | 불가 | 없음 | 서버에서 발급한 UUID 문자열, PK |
| name | VARCHAR(200) | 불가 | 없음 | 필수 이름, 앞뒤 공백 제거 |
| description | VARCHAR(2000) | 허용 | 없음 | 선택 설명, 앞뒤 공백 제거·빈 값은 NULL |
| created_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 생성 날짜와 시간을 함께 저장한 생성일시 |
| updated_at | TIMESTAMP(6) WITH TIME ZONE | 불가 | CURRENT_TIMESTAMP | 최종 수정일시, 생성 시 created_at과 동일 |
| created_by | VARCHAR(100) | 불가 | '0' | 서버 인증 정보의 내부 user_key |
| updated_by | VARCHAR(100) | 불가 | '0' | 생성 시 created_by와 동일 |

## 기록 규칙과 제약

- 생성일과 생성시간을 DATE·TIME이나 문자열 컬럼으로 분리하지 않습니다. UTC 절대 시점을 마이크로초 정밀도 타임스탬프로 저장하고 Java에서는 OffsetDateTime을 사용합니다.
- 공통 컬럼은 [공통 스키마 규칙](../../schema-rules.md)을 따릅니다. 시스템 INSERT용 공통 기본값은 유지하되 생성 API의 INSERT는 모든 값을 명시해 기본값에 의존하지 않습니다.
- 서비스가 시각을 한 번 얻어 생성일시·수정일시에 동일하게 전달합니다. ID·작업자·시각을 생성 요청에서 받지 않습니다. 사용자 생성 API는 내부 user_key를 기록하며 시스템 예약값 `'0'`으로 대체하지 않습니다. user_id·username 변경 시에도 감사 값은 바뀌지 않습니다. [ADR-031](../../../decisions/ADR-031-user-identity-fields.md)을 따릅니다.
- PK는 pk_dataset(dataset_id)이며 이름 고유 제약·작업자 FK·추가 인덱스는 없습니다.
- 길이 제한은 공백 제거 전 입력 길이에 적용합니다. 빈 이름과 공백만 있는 이름은 거부합니다. HTTP 입력과 도메인 모두 유효성을 확인합니다.

## 관련 문서

- [API 계약](../../../api/api.md#데이터셋-생성)
- [ADR-030](../../../decisions/ADR-030-dataset-creation-contract.md)
