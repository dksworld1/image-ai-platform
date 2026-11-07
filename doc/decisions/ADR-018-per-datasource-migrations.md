# ADR-018: 데이터소스별 Flyway 구성

- 상태: 채택
- 날짜: 2026-11-07

## 맥락

업무 DB와 추가 H2 파일 DB에 독립적인 버전 SQL을 적용해야 합니다.

## 대안

하나의 Flyway에 여러 경로를 지정하면 같은 연결에 모든 SQL이 적용됩니다.

## 결정

두 번째 데이터소스 이름은 `audit`으로 정합니다. Spring이 각 Flyway 빈을 초기화할 때 명시적 qualifier로 지정한 데이터소스와 해당 YAML 경로를 조합해 migrate를 호출합니다. local/test 프로파일에서 활성화하고 legacy catalog/archive는 대상에서 제외합니다.

위치 설정은 app-locations와 audit-locations 목록으로 바인딩합니다. 목록이 비어 있으면 해당 DB 적용을 건너뜁니다. 상세 설정은 [DB 마이그레이션 설정](../operations/database-migration.md)에 기록합니다.

## 결과

각 DB에서 버전 번호와 이력을 독립적으로 관리합니다. 연결 설정과 활성화 방법은 [배포 문서](../operations/deployment.md)에 기록합니다.
