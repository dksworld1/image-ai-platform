# Image AI Platform

React + TypeScript 프런트엔드와 Spring Boot REST API로 구성된 모노레포 보일러플레이트입니다.

## 필요 환경

- Node.js 22.12 이상 및 npm
- Java 21 이상
- Maven 3.6.3 이상

## 로컬 실행

Linux 및 macOS에서는 Java 21 이상과 Maven 3.6.3 이상을 준비한 뒤 저장소 루트에서 백엔드를 실행합니다.

```bash
./bin/start-backend.sh
```

`local` 프로필로 실행하며, 포트는 Windows와 동일하게 `bin/local.settings.ps1`의 `BackendPort`에서 읽습니다. Maven이 Java 21 이상을 사용하도록 준비합니다. 백엔드 디렉토리를 기준으로 실행하므로 로컬 H2 데이터는 `backend/data/`에 저장됩니다. 상태 확인은 `curl http://localhost:8080/api/health`, 종료는 Ctrl+C입니다.

저장소 루트에서 다음 명령을 실행하면 두 서버가 각각 별도의 PowerShell 창에서 동시에 시작됩니다.

```powershell
./bin/start-local.ps1
```

실행 창은 `-NoExit` 옵션으로 유지되므로 시작 오류와 로그를 확인할 수 있습니다. `start-local.ps1` 실행 창도 서버 창을 띄운 뒤 Enter 입력을 기다립니다. 이 창에서 Enter를 눌러도 서버 창은 계속 열려 있습니다. 서버가 종료된 뒤에는 각 서버 창이 Enter 입력을 기다립니다. 서버 창을 닫으면 해당 서버가 종료됩니다. 포트나 빌드 설정은 [bin/local.settings.ps1](bin/local.settings.ps1)에서 수정할 수 있습니다.

프런트엔드 스크립트는 최초 실행 시 의존성을 설치하고 정적 파일을 빌드한 뒤 `http://localhost:4173`에서 제공합니다. 백엔드는 `http://localhost:8080`에서 실행됩니다. 정적 서버는 `/api` 요청을 백엔드로 프록시하므로 로컬 상태 표시도 동작합니다. 서버를 각각 실행하려면 `./bin/start-frontend.ps1`, `./bin/start-backend.ps1`을 사용합니다. 개별 실행 시에도 스크립트가 끝나면 Enter 입력을 기다려 창이 바로 닫히지 않습니다.

`local` 및 `test` 프로파일은 개발·검증 편의를 위해 YAML에 고정 JWT 서명 키를 둡니다. 이 키는 로컬 테스트 전용 예외이며 외부 환경이나 운영에서 사용하지 않습니다. `dev` 및 `prod`에서는 외부 인증 YAML로 UTF-8 기준 32바이트 이상의 별도 비밀 키를 주입해야 합니다.


기본 역할이나 권한 할당은 자동 생성하지 않습니다. 현재 권한 판정 서비스는 실제 DB와 API 인가 인터셉터에 연결되어 있습니다. 컨트롤러에 `@PublicApi`(공개), `@RequirePermission`(인증만 필요), `@RequirePermission("dataset:item:read")`(세부 권한 필요)로 선언합니다. 정책 없는 요청은 기본 거부하며 기동 검사나 권한 자동 등록은 하지 않습니다.

로컬과 테스트에서는 Spring이 `app`과 `audit` 데이터소스별로 Flyway를 실행합니다. 업무 스키마는 `db/migration/app`, 두 번째 DB의 스키마는 `db/migration/audit` 클래스패스 경로에서 관리합니다. `@Sql`은 테스트 데이터 준비에 사용합니다.

Vite 핫 리로드를 사용하는 개발 모드는 `cd frontend; npm run dev`로 실행하며 주소는 `http://localhost:5173`입니다.

## DB 마이그레이션 설정

local/test는 시작 시 Flyway를 실행합니다. app·audit DB별로 위치 목록과 적용 이력을 관리하며 dev/prod는 기본 비활성화입니다.

```yaml
app:
  migration:
    enabled: true
    app-locations:
      - classpath:db/migration/app
    audit-locations:
      - classpath:db/migration/audit
```

seed가 필요한 프로파일에서는 각 목록에 seed 경로를 추가합니다. 프로파일별 목록은 공통 목록 전체를 대체합니다. 위치 목록이 비어 있으면 해당 DB의 적용을 건너뜁니다. 공통 목록을 비우려면 `app-locations: []` 또는 `audit-locations: []`를 명시합니다.

[DB 마이그레이션 설정](doc/operations/database-migration.md)에서 파일 배치, seed 예시, 외부 YAML과 건너뛰기 설정을 확인할 수 있습니다.

## 검증 명령

프런트엔드 빌드는 다음 명령으로 실행합니다.

```powershell
Set-Location frontend
npm run build
```

Windows에서 백엔드 테스트는 Java 21을 쓰도록 현재 PowerShell 세션에 환경 변수를 지정한 뒤 실행합니다. 머신 전역 설정은 바꾸지 않습니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
Set-Location ..\backend
mvn test
```

## 설정

호스트 서버의 비밀정보 YAML 관리와 단일·다중 외부 파일을 지정하는 JAR 실행 방법은 [배포 및 서버 설정 운영](doc/operations/deployment.md)을 참고하세요.

런타임 설정 로더는 INFO로 탐색할 클래스패스 폴더와 로딩을 완료한 각 파일의 전체 리소스 URL을 출력합니다. 대상 폴더나 `.properties` 파일이 없으면 `config/runtime/` 폴더와 UTF-8 설정 파일을 생성하라는 WARN을 출력합니다. 설정값은 로그에 출력하지 않습니다.

Spring 초기화 전 런타임 설정은 `backend/src/main/resources/config/runtime/` 바로 아래의 UTF-8 `*.properties`에서 읽습니다. 하위 디렉터리는 제외하고 중복 키·동일 경로 리소스는 오류 처리합니다. `RuntimeConfig.settings()` 최초 접근 시 클래스 초기화로 한 번 로딩하며 `main()` 초기화 호출은 필요하지 않습니다. 현재 실제 설정 항목은 없고, 새 항목은 `foundation.config.RuntimeSettings`의 명시적 필드와 `RuntimeSettingsLoader`의 변환·검증·매핑을 함께 추가합니다. 비밀값을 클래스패스 파일에 넣어 패키징하지 않습니다. 일반 Spring 설정은 YAML로 관리하며 기존 환경 변수 전환은 아직 수행하지 않았습니다.

프런트엔드는 `/api`를 사용하며 백엔드 포트와 허용 출처는 Spring YAML에서 관리합니다.

## 프로젝트 문서

문서 목록과 갱신 방법은 [doc/README.md](doc/README.md)를 참고하세요. 기능 구현 단계와 핵심 개념은 [doc/stages/README.md](doc/stages/README.md)에서 추적합니다. 저장소 루트의 [AGENTS.md](AGENTS.md)에는 프로젝트 구성과 문서 갱신 규칙이 있습니다.
