# 배포 및 서버 설정 운영

갱신일: 2026-11-07

## 목적과 적용 범위

백엔드 fat JAR 실행 시 호스트 서버의 외부 YAML을 로딩하는 방법과 비밀정보 관리 방침을 설명합니다. 현재 구현과 실행 조건을 기준으로 작성하며 미구현 변경은 적용된 것으로 설명하지 않습니다. 서버 설정·비밀정보 주입·실행 옵션·배포 절차가 변경되면 같은 변경에서 이 문서를 갱신합니다.

## 호스트 서버의 비밀정보 관리

- DB 사용자명·비밀번호와 JWT 서명 키 등 환경별 비밀정보는 호스트 서버의 외부 YAML에 저장합니다. 프로젝트 설정에 환경 변수를 사용하지 않습니다.
- 파일은 UTF-8로 저장하고 JDBC·인증 등 주제별로 나눕니다. 실제 비밀값을 저장소, JAR, 컨테이너 이미지 또는 배포 아카이브에 포함하지 않습니다.
- 예시 경로는 Windows의 `C:/secure/`, Linux의 `/etc/image-ai/`입니다. 서버에서 관리할 절대 경로를 정하고 앱 실행 계정에 읽기 권한, 운영 관리 주체에 필요한 수정 권한만 부여합니다. 상위 디렉터리 접근 권한도 함께 제한합니다.
- 실행 명령에는 파일 경로만 지정하고 비밀값을 직접 넣지 않습니다. 파일 내용을 로그·실행 결과·문서에 복사하지 않으며, 백업 파일도 같은 접근 정책으로 관리합니다.
- 비밀값 변경 후에는 앱을 재시작합니다. 현재 실행 중 자동 재로딩 기능은 없습니다.

예시 파일 구성:

```text
C:/secure/
  jdbc.yaml
  auth.yaml
```

`jdbc.yaml`의 구조 예시입니다. 아래 값은 실제 접속 정보로 바꿔야 하는 자리표시자입니다. 드라이버에 필요한 의존성이 배포 JAR에 포함되어 있어야 합니다.

```yaml
app:
  datasource:
    url: "<업무 DB JDBC URL>"
    username: "<업무 DB 사용자명>"
    password: "<업무 DB 비밀번호>"
    driver-class-name: "<JDBC 드라이버 클래스>"
  datasources:
    catalog:
      url: "<catalog DB JDBC URL>"
      username: "<catalog DB 사용자명>"
      password: "<catalog DB 비밀번호>"
      driver-class-name: "<JDBC 드라이버 클래스>"
    archive:
      url: "<archive DB JDBC URL>"
      username: "<archive DB 사용자명>"
      password: "<archive DB 비밀번호>"
      driver-class-name: "<JDBC 드라이버 클래스>"
```

현재 일반 실행은 업무 `app` DB 외에 legacy `catalog`·`archive` 데이터소스도 구성하므로 dev/prod 외부 파일에 필요한 연결 설정을 제공합니다.

`auth.yaml`의 구조 예시입니다. 운영용 키는 UTF-8 기준 32바이트 이상의 별도 비밀 키로 준비하고 아래 자리표시자를 교체합니다.

```yaml
app:
  auth:
    jwt:
      secret: "<운영 전용 JWT 서명 키>"
```

## additional-location으로 외부 파일 추가

`spring.config.additional-location`은 기본 설정 위치를 유지하면서 외부 파일을 추가합니다. 외부 파일에 동일한 키가 있으면 내부 YAML 값을 덮어쓰고, 외부에 없는 항목은 내부 설정을 유지합니다. 환경 변수·JVM 시스템 프로퍼티·명령행에서 같은 설정 키를 별도 지정하면 해당 소스가 YAML보다 우선할 수 있으므로 비밀값은 외부 YAML에 모아 관리합니다.

단일 파일 예시입니다. `local`은 설명용이며 운영 배포는 아래 dev/prod 절차를 따릅니다.

```powershell
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --spring.config.additional-location=file:C:/secure/jdbc.yaml
```

이 경우 내부 `application-local.yaml`이 import하는 `jdbc-local.yaml`의 값 위에 외부 JDBC 설정을 적용합니다. 다른 프로파일의 로컬 기본값을 운영값으로 간주하지 않습니다.

### 파일이 여러 개인 경우

쉼표로 파일 경로를 나열합니다. 동일 키가 여러 추가 파일에 있으면 뒤에 지정한 파일이 우선합니다. 주제별 파일에서는 가능한 한 키가 겹치지 않게 관리합니다.

```powershell
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local "--spring.config.additional-location=file:C:/secure/jdbc.yaml,file:C:/secure/auth.yaml"
```

공백이 포함된 경로는 위 예시처럼 옵션 전체를 따옴표로 감쌉니다. 각 파일 경로에 `file:`을 붙이고 파일명을 명시합니다. 파일 목록에서 쉼표 뒤에 불필요한 공백을 넣지 않습니다.

디렉터리를 지정할 수도 있지만, 이때는 마지막 `/`가 필요하고 기본적으로 `application.yaml`과 `application-{profile}.yaml` 같은 이름을 탐색합니다. 해당 디렉터리의 임의 이름 YAML을 전부 읽는 기능이 아니므로 `jdbc.yaml`, `auth.yaml`처럼 관리하는 경우 파일을 각각 지정합니다. 프로파일별 변형 파일이 있으면 그 파일도 로딩 대상이므로 함께 관리합니다.

필수 서버 설정에는 `optional:`을 붙이지 않습니다. 지정 파일이 없으면 시작 실패로 드러나게 합니다. `spring.config.location`은 기본 탐색 위치를 대체하므로 기존 설정을 유지하며 값을 추가하는 용도로 사용하지 않습니다.

### dev/prod 실행

서버의 JDBC·인증 YAML 경로를 지정합니다.

```powershell
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod "--spring.config.additional-location=file:C:/secure/jdbc.yaml,file:C:/secure/auth.yaml"
```

Linux 예시:

```bash
java -jar backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod --spring.config.additional-location=file:/etc/image-ai/jdbc.yaml,file:/etc/image-ai/auth.yaml
```


## 정적 런타임 설정과의 경계

### 데이터소스별 DB 설정

프로파일 활성화, 다중 SQL 위치, seed, 빈 목록 처리는 [DB 마이그레이션 설정](database-migration.md)을 따릅니다.

local/test의 `audit` 데이터소스는 업무 DB와 별도의 H2 파일을 사용합니다. backend 기준 local 경로는 `./data/image-ai-audit`, test 경로는 `./target/image-ai-platform-audit-test`입니다. dev/prod에서 사용할 경우 외부 JDBC YAML에 `app.datasources.audit.url`, 사용자명, 비밀번호와 드라이버를 지정합니다.

Flyway 실행은 `app.migration.enabled`로 제어하며 기본값은 false, local/test에서는 true입니다. `app-locations`와 `audit-locations`은 같은 설정 그룹에서 각 DB의 SQL 경로를 지정합니다. 각 DB가 자기 이력 테이블을 관리합니다. 현재 audit의 실제 업무 SQL은 없습니다.

로컬에서는 Vite 개발 서버와 미리보기 서버가 시작 시 `bin/local.settings.ps1`의 `BackendPort`를 읽습니다. 포트를 바꾸면 백엔드와 Vite를 재시작합니다. 브라우저는 항상 같은 출처의 `/api`를 호출하므로 정적 파일 배포 시에는 Nginx의 `/api/` 프록시 대상에 배포 환경의 백엔드 주소를 지정합니다.

`RuntimeConfig`는 클래스패스 `config/runtime/*.properties`를 자체 로딩합니다. Spring의 `additional-location`은 이 정적 로더의 탐색 경로나 값을 변경하지 않습니다. DB 자격 증명은 Spring 데이터소스 생성에 사용하므로 외부 YAML에서 관리합니다. 런타임 로더의 폴더·파일 INFO/WARN 로그는 Spring이 읽은 외부 YAML의 로딩 목록을 의미하지 않습니다.

## 관련 문서와 유지 관리

- [아키텍처](../architecture/architecture.md)
- [애플리케이션 설정 모듈](../stages/stage-application-settings.md)
- [ADR-004 외부 JDBC YAML](../decisions/ADR-004-datasource-config-files.md)
- [ADR-013 Spring YAML 우선 관리](../decisions/ADR-013-topic-based-spring-yaml.md)
- [Spring Boot 외부 설정 공식 문서](https://docs.spring.io/spring-boot/reference/features/external-config.html): 추가 위치, 우선순위, 파일 import, 디렉터리와 프로파일별 파일 처리의 기준

향후 외부 설정 경로·파일 구성·비밀정보 전달·프로파일·JAR 실행 옵션·배포 절차를 변경하면 이 문서의 명령, 예시 YAML, 현재 구현 제약과 검증 여부를 같은 변경에서 갱신합니다. 코드에서 제거한 호환 옵션은 문서에서도 제거하고 실행하지 않은 절차를 검증 완료로 기록하지 않습니다.
