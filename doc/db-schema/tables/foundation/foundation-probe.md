# `foundation_probe` 테이블 정의

- 그룹: 기반
- 데이터소스: legacy `catalog`, `archive` (자동 생성 없음, fixture 또는 사전 DB 프로비저닝 필요)
- 스키마 프로비저닝: 애플리케이션 자동 생성 없음. DB 정의서만 관리
- 사용 코드: `com.imageaiplatform.foundation.dao.FoundationProbeDao`

## 목적

기반 데이터 접근과 데이터소스 연결을 확인하는 예제 테이블입니다. 제품 업무 데이터 저장에는 사용하지 않습니다.

## 컬럼

| 컬럼 | 형식 | NULL | 기본값 | 설명 |
|---|---|---:|---|---|
| `id` | `BIGINT IDENTITY` | 아니요 | 자동 증가 | 레코드 식별자, 기본 키 |
| `probe_value` | `VARCHAR(100)` | 아니요 | 없음 | 연결·트랜잭션 검증 값 |

## 제약 및 인덱스

- 기본 키: `id`
- 추가 인덱스 및 유일성 제약 없음
