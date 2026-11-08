# ADR-003: 프로파일별 로그 설정 파일 분리

- 상태: 채택
- 날짜: 2026-11-01
- 관련 기록: [1단계 백엔드 프레임워크와 데이터 기반](../stages/backend/stage-01-backend-foundation.md)

## 맥락

`prod`, `dev`, `local` 환경은 진단 수준과 로그 출력 요구가 다를 수 있습니다. 환경별 정책을 분리하면서 Spring Boot가 로그 시스템을 초기화할 때 올바른 설정 파일을 선택해야 합니다.

## 대안

- 단일 로그 설정 파일에서 활성 프로파일 조건으로 정책을 분기합니다.
- 환경별 로그 설정 파일을 두고 각 프로파일 설정에서 파일 경로를 명시합니다.

## 결정

- 프로파일별 로그 설정 파일을 `backend/src/main/resources/config/logger/`에 둡니다.
- Logback을 사용하는 경우 `logback-local.xml`, `logback-dev.xml`, `logback-prod.xml`로 분리합니다.
- 각 Spring 프로파일 설정의 `logging.config`에서 `classpath:config/logger/logback-{profile}.xml`을 참조합니다.
- 공통 설정 재사용 방식, 로그 레벨, 출력 형식 및 운영 수집 경로는 구현·운영 설계에서 정합니다.

## 결과

- 프로파일별 로그 정책을 독립적으로 변경하고 검토할 수 있습니다.
- 설정 파일은 `config/logger/logback-{profile}.xml`로 구현했으며 local 프로파일 스모크 테스트와 JAR의 리소스 포함 여부를 확인했습니다.
- 민감 정보를 로그에 기록하지 않도록 모든 프로파일에서 공통 원칙을 적용합니다.
- Spring Boot는 `logging.config`로 classpath 로그 설정을 지정할 수 있습니다. 로깅 초기화가 ApplicationContext 생성보다 앞서므로 경로는 초기 외부 설정에서 제공해야 합니다.
