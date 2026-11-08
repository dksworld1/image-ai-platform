# ADR-019: 이름별 데이터소스 설정 구조

- 상태: 채택
- 날짜: 2026-11-07
- 관련 기록: ADR-004·ADR-010의 설정 경로를 대체

## 맥락

업무 DB와 추가 DB의 설정 경로가 서로 다른 구조를 사용했습니다.

## 대안

기존 경로를 유지하거나 이름별 경로로 통일할 수 있습니다.

## 결정

연결 설정은 `datasource.app`, `datasource.audit`, `datasource.catalog`, `datasource.archive`에 둡니다. 각 경로의 `hikari`가 연결 풀 설정을 담당합니다. 데이터소스 선택은 기존 qualifier를 사용합니다.

## 결과

서버 외부 JDBC YAML도 같은 구조로 갱신해야 합니다. 설정 예시는 [배포 문서](../operations/deployment.md)에 기록합니다.
