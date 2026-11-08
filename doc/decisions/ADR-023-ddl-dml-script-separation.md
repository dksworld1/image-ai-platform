# ADR-023: 마이그레이션의 DDL·DCL과 seed DML 분리

- 상태: 채택
- 날짜: 2026-11-08
- 관련 기록: [ADR-018](ADR-018-per-datasource-migrations.md), [ADR-022](ADR-022-local-app-seed.md)

## 맥락

사용자는 데이터 변경 DML을 seed에, DDL·DCL을 migration에 두도록 요청했습니다. 기존 V3는 테이블 생성과 권한 데이터 등록을 함께 담고 있습니다.

## 대안

- 기존 V3 유지: 적용 이력은 보존되지만 요청한 파일 분리를 충족하지 못합니다.
- V3에서 DML을 분리: 구조와 데이터 책임을 나누며 기존 적용 DB의 체크섬 정합성 처리가 필요합니다.

## 결정

요청에 따라 V3의 권한 INSERT를 공통 `db/seed/app/R__01_ui_permissions.sql`로 이동합니다. 공통 app 위치 목록은 migration과 공통 seed를 포함하고 local은 로컬 seed도 추가합니다. 권한·역할 테이블의 데이터 연결은 DML이며 DB 자체 권한을 제어하는 DCL과 구분합니다. 새 SQL도 같은 분리 원칙을 적용합니다.

버전 DDL 이후 반복 seed를 실행하며 공통 권한 seed의 이름을 `01_ui_permissions`로 두어 `local_app_seed`보다 먼저 적용합니다. 의존하는 seed의 재실행 여부는 각각의 체크섬으로 관리합니다.

## 결과

새 DB는 V1~V3 DDL과 공통 권한 seed, 필요 시 로컬 seed 순서로 적용합니다. 요청에 따른 기존 V3 편집으로 이미 V3를 적용한 DB는 checksum mismatch가 발생합니다. 기존 두 INSERT가 없는 변경본임을 확인하고 DB를 백업한 뒤, 동일 DB·위치 목록의 Flyway `repair()`로 이력을 맞추고 `migrate()`를 실행해야 합니다. repair는 데이터를 옮기거나 복구하지 않습니다. 공통 seed는 기존 권한 행을 중복 삽입하지 않습니다.

기동 코드에서 자동 repair하거나 이력 SQL을 직접 수정하지 않습니다. 실제 로컬·운영 DB의 repair는 이번 파일 이관 작업에서 수행하지 않습니다. 이전 ADR-022의 기존 V3 DB 즉시 적용 설명은 이 변경 이후 체크섬 정리 절차가 선행되는 것으로 보완합니다.
