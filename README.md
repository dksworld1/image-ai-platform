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

`local` 프로필로 실행하며, 포트는 Windows와 동일하게 `bin/local.settings.ps1`의 `BackendPort`에서 읽습니다. `JAVA_HOME`과 `MAVEN_HOME`으로 현재 실행에 사용할 도구를 지정할 수 있습니다. 백엔드 디렉토리를 기준으로 실행하므로 로컬 H2 데이터는 `backend/data/`에 저장됩니다. 상태 확인은 `curl http://localhost:8080/api/health`, 종료는 Ctrl+C입니다.

저장소 루트에서 다음 명령을 실행하면 두 서버가 각각 별도의 PowerShell 창에서 동시에 시작됩니다.

```powershell
./bin/start-local.ps1
```

실행 창은 `-NoExit` 옵션으로 유지되므로 시작 오류와 로그를 확인할 수 있습니다. `start-local.ps1` 실행 창도 서버 창을 띄운 뒤 Enter 입력을 기다립니다. 이 창에서 Enter를 눌러도 서버 창은 계속 열려 있습니다. 서버가 종료된 뒤에는 각 서버 창이 Enter 입력을 기다립니다. 서버 창을 닫으면 해당 서버가 종료됩니다. 포트나 빌드 설정은 [bin/local.settings.ps1](bin/local.settings.ps1)에서 수정할 수 있습니다.

프런트엔드 스크립트는 최초 실행 시 의존성을 설치하고 정적 파일을 빌드한 뒤 `http://localhost:4173`에서 제공합니다. 백엔드는 `http://localhost:8080`에서 실행됩니다. 정적 서버는 `/api` 요청을 백엔드로 프록시하므로 로컬 상태 표시도 동작합니다. 서버를 각각 실행하려면 `./bin/start-frontend.ps1`, `./bin/start-backend.ps1`을 사용합니다. 개별 실행 시에도 스크립트가 끝나면 Enter 입력을 기다려 창이 바로 닫히지 않습니다.

`local` 및 `test` 프로파일은 개발·검증 편의를 위해 YAML에 고정 JWT 서명 키를 둡니다. 이 키는 로컬 테스트 전용 예외이며 외부 환경이나 운영에서 사용하지 않습니다. `dev` 및 `prod`에서는 `JWT_SECRET` 환경 변수로 UTF-8 기준 32바이트 이상의 별도 비밀 키를 주입해야 합니다.

Flyway는 local/test에서 앱 시작 시 `app` 데이터소스에 미적용 migration을 실행합니다. dev/prod의 일반 앱 실행은 migration을 건너뜁니다. 배포 전 migration 전용 실행은 대상 DB 설정을 담은 `APP_CONFIG_FILE`과 함께 수행합니다.

V2는 역할·권한 및 사용자 역할·역할 권한 연결 테이블을 생성합니다. V3는 초기 `ADMIN`·`MEMBER` 역할과 `auth_audit_log`를 추가합니다. V4는 ADMIN에 사용자 관리 권한을 연결합니다. 계정과 사용자 역할 할당은 일반 서버 시작 시 자동 생성하지 않습니다. 현재 권한 판정 서비스는 실제 DB와 API 인가 인터셉터에 연결되어 있습니다. 컨트롤러에 `@PublicApi`(공개), `@RequirePermission`(인증만 필요), `@RequirePermission("dataset:item:read")`(세부 권한 필요)로 선언합니다. 정책 없는 요청은 기본 거부하며 기동 검사나 권한 자동 등록은 하지 않습니다.

테스트 전용 데이터는 `backend/src/main/resources/db/seed/`의 Flyway 반복 SQL 파일(`R__...sql`)로 관리하며 `test` 프로파일에서만 적용합니다. 현재 `test-user` 샘플 계정은 테스트 용도입니다.

```powershell
$env:APP_CONFIG_FILE = 'C:\secure\image-ai-prod.yaml'
java -jar backend\target\backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod --spring.flyway.enabled=true --app.database-migration-only=true
```

이 명령은 웹 서버와 legacy `catalog`·`archive` 연결을 시작하지 않고 `app` DB에만 migration을 적용합니다. 성공한 뒤 일반 앱을 배포합니다.

Vite 핫 리로드를 사용하는 개발 모드는 `cd frontend; npm run dev`로 실행하며 주소는 `http://localhost:5173`입니다.

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

프런트엔드 API 기본 경로는 `/api`이며 `VITE_API_BASE_URL`로 변경할 수 있습니다. 백엔드 포트와 허용할 프런트엔드 출처는 `SERVER_PORT`, `CORS_ALLOWED_ORIGIN` 환경 변수로 설정합니다.

`local` 프로필의 CORS 기본 허용 출처는 `http://localhost:5173`(Vite 개발)과 `http://localhost:4173`(미리보기)입니다. `application-local.yaml`에서 관리하며 `CORS_ALLOWED_ORIGIN`으로 쉼표로 구분한 출처 목록을 지정해 덮어쓸 수 있습니다. `.env.example`은 환경 변수 예시이며 백엔드가 `.env` 파일을 자동으로 읽지는 않습니다.

## 최초 관리자 생성

최초 관리자 생성은 일반 서버 시작과 분리한 일회성 명령입니다. 대상 DB를 사용하는 서버를 중지하고 패키징한 뒤 실행합니다. 아래 명령은 저장소 루트 기준이며 비밀번호는 콘솔에서 입력합니다.

```bash
export INITIAL_ADMIN_USERNAME=admin
read -rsp 'Initial administrator password: ' INITIAL_ADMIN_PASSWORD
export INITIAL_ADMIN_PASSWORD
java -jar backend/target/backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --app.admin-bootstrap-only=true
unset INITIAL_ADMIN_PASSWORD
```

이 모드는 웹 서버·legacy DB를 시작하지 않고 app DB 마이그레이션 후 최초 계정·ADMIN 역할 할당·감사 기록을 함께 저장하고 종료합니다. dev/prod는 대상 프로필과 기존 `APP_CONFIG_FILE`을 지정합니다. ADMIN은 V4에서 `auth:user:manage` 권한을 부여받으며 MEMBER에는 자동 권한을 부여하지 않습니다. 비밀번호는 비어 있으면 안 되고 BCrypt 제한인 UTF-8 72바이트 이하여야 합니다. 고정 기본 비밀번호는 없습니다.

이미 ADMIN 할당이 있거나 같은 사용자명이 존재하면 계정 생성·비밀번호 변경 없이 실패합니다. 모든 관리자가 비활성이어도 재생성·복구 용도로 사용하지 않습니다. 정상 서비스 경로에서는 마지막 활성 관리자의 ADMIN 회수와 비활성화를 거부합니다. 관리자 변경은 같은 DB 행 잠금과 READ_COMMITTED 트랜잭션을 사용하며, 호환되지 않는 외부 트랜잭션에 참여하면 변경을 거부합니다. 직접 SQL 변경은 이 서비스 보호 범위 밖입니다.

## 프로젝트 문서

문서 목록과 갱신 방법은 [doc/README.md](doc/README.md)를 참고하세요. 기능 구현 단계와 핵심 개념은 [doc/stages/README.md](doc/stages/README.md)에서 추적합니다. 저장소 루트의 [AGENTS.md](AGENTS.md)에는 프로젝트 구성과 문서 갱신 규칙이 있습니다.
