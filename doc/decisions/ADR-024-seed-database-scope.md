# ADR-024: DB와 적용 범위에 따른 seed 폴더 분류

- 상태: 채택
- 날짜: 2026-11-08
- 대체/관련 기록: [ADR-022](ADR-022-local-app-seed.md), [ADR-023](ADR-023-ddl-dml-script-separation.md)의 seed 경로·분류를 보완

## 맥락

사용자는 app 공통 데이터를 seed/app/common, local 프로파일 데이터를 seed/app/local로 관리하도록 요청했습니다. 기존 로컬 seed에는 환경과 무관한 화면·메뉴 정의도 섞여 있었습니다.

## 대안

- 기존 범위별/DB별 혼합 경로 유지: 요청한 DB 중심 분류와 일치하지 않습니다.
- DB별 경로 아래 common·local 구분: 데이터 소유 DB와 적용 범위가 명확합니다.

## 결정

`db/seed/app/common`에 권한 정의와 시작·관리 화면·메뉴 정보를, `db/seed/app/local`에 로컬 admin 계정·역할·권한 연결을 둡니다. 기본 위치는 common만 포함하고 local 프로파일에서 local을 추가합니다. 상위 app 경로 전체는 재귀 탐색으로 local 데이터까지 읽을 수 있어 지정하지 않습니다.

실행 순서는 반복 SQL 이름에 따라 `01_ui_permissions` → `02_ui_navigation` → `local_app_seed`입니다. DML은 seed, DDL·DCL은 migration 원칙을 유지합니다.

## 결과

공통 데이터만 적용하면 화면·메뉴는 존재하지만 로컬 관리자 계정은 생성되지 않습니다. dev/prod는 기존처럼 마이그레이션을 명시적으로 활성화해야 합니다. 기존 두 SQL의 파일명은 유지하고 새 화면·메뉴 SQL만 분리합니다. 경로 이동 자체는 새 이력을 만들지 않으며 내용이 바뀐 local 반복 SQL은 재적용됩니다. 이전 V3 편집에 따른 체크섬 정리 정책은 ADR-023을 그대로 따릅니다. 관리 React 화면 구현과 실제 DB 이력 수정은 이번 폴더 정리 범위에 없습니다.
