# ADR-004: YAML 파일 기반 데이터소스 설정

- 상태: 채택
- 날짜: 2026-11-01
- 대체 대상: [ADR-002의 연결 정보 전달 방식](ADR-002-backend-data-access-and-local-test-database.md)
- 관련 단계: [1단계 백엔드 프레임워크와 데이터 기반](../stages/stage-01-backend-foundation.md)

## 맥락

JDBC 연결 주소, 사용자, 암호, 연결 풀 설정을 각각 환경변수로 관리하면 항목이 늘어날수록 환경별 설정 관리가 번거로워집니다. 운영 비밀정보는 소스 저장소와 배포 산출물에서 분리하면서 설정 파일 단위로 관리할 필요가 있습니다.

## 대안

- 각 연결 속성을 독립적인 환경변수로 전달합니다.
- 프로파일별 YAML 설정 파일에 연결 속성을 모으고 Spring Boot 외부 설정 import로 읽습니다.

## 결정

- JDBC 연결 정보와 HikariCP 풀 설정은 YAML 파일에 둡니다.
- 공통 설정은 `application.yaml`, 환경별 가져오기 설정은 `application-{profile}.yaml`에 둡니다.
- JDBC 파일은 `config/datasource/` 아래에 둡니다. 로컬 H2 설정은 classpath의 `config/datasource/jdbc-local.yaml`을 사용합니다.
- 자동화 테스트의 파일 기반 H2 설정은 classpath의 `config/datasource/jdbc-test.yaml`을 테스트 프로파일에서 가져옵니다.
- 각 JDBC 파일은 `app.datasources.catalog`과 `app.datasources.archive`에 DB별 연결 및 HikariCP 설정을 둡니다.
- 위 항목은 기존 foundation 데이터소스 설정입니다. 신규 업무용 `app.datasource`는 [ADR-010](ADR-010-app-datasource.md)에서 추가했습니다.
- `dev`와 `prod`는 `APP_CONFIG_FILE` 환경변수에 외부 YAML 파일 경로를 전달하고 `spring.config.import=file:${APP_CONFIG_FILE}` 방식으로 읽습니다. 필수 파일이 없으면 해당 프로파일은 시작에 실패해야 합니다.
- 서버 관리 외부 파일과 비밀 값은 저장소 또는 실행 JAR에 넣지 않습니다. 환경변수는 파일 경로만 전달합니다.

## 결과

- 한 파일에서 JDBC 및 풀 설정을 환경별로 검토하고 관리할 수 있습니다.
- 파일 권한과 배포 절차로 외부 설정을 보호해야 합니다. 운영 파일은 서버에서만 관리합니다.
- 파일 누락, 읽기 권한 오류 및 잘못된 YAML은 애플리케이션 시작 실패로 드러나도록 해야 합니다.
- 프로파일별 YAML import 및 classpath JDBC 설정 파일을 구현했습니다. local 설정 로딩 테스트와 패키징 JAR의 프로파일 설정 포함 여부를 확인했습니다. `dev`/`prod` 외부 파일 import는 지정한 필수 경로를 읽으며, 실제 환경 파일 제공은 배포 환경에서 담당합니다.

현재 설정 경로는 [ADR-019](ADR-019-datasource-settings-tree.md)의 datasource.<이름> 구조를 따릅니다.
