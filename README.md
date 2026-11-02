# Image AI Platform

React + TypeScript 프런트엔드와 Spring Boot REST API로 구성된 모노레포 보일러플레이트입니다.

## 필요 환경

- Node.js 22.12 이상 및 npm
- Java 21 이상
- Maven 3.6.3 이상

## 로컬 실행

저장소 루트에서 다음 명령을 실행하면 두 서버가 각각 별도의 PowerShell 창에서 동시에 시작됩니다.

```powershell
./bin/start-local.ps1
```

실행 창은 `-NoExit` 옵션으로 유지되므로 시작 오류와 로그를 확인할 수 있습니다. `start-local.ps1` 실행 창도 서버 창을 띄운 뒤 Enter 입력을 기다립니다. 이 창에서 Enter를 눌러도 서버 창은 계속 열려 있습니다. 서버가 종료된 뒤에는 각 서버 창이 Enter 입력을 기다립니다. 서버 창을 닫으면 해당 서버가 종료됩니다. 포트나 빌드 설정은 [bin/local.settings.ps1](bin/local.settings.ps1)에서 수정할 수 있습니다.

프런트엔드 스크립트는 최초 실행 시 의존성을 설치하고 정적 파일을 빌드한 뒤 `http://localhost:4173`에서 제공합니다. 백엔드는 `http://localhost:8080`에서 실행됩니다. 정적 서버는 `/api` 요청을 백엔드로 프록시하므로 로컬 상태 표시도 동작합니다. 서버를 각각 실행하려면 `./bin/start-frontend.ps1`, `./bin/start-backend.ps1`을 사용합니다. 개별 실행 시에도 스크립트가 끝나면 Enter 입력을 기다려 창이 바로 닫히지 않습니다.

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

## 프로젝트 문서

문서 목록과 갱신 방법은 [doc/README.md](doc/README.md)를 참고하세요. 기능 구현 단계와 핵심 개념은 [doc/stages/README.md](doc/stages/README.md)에서 추적합니다. 저장소 루트의 [AGENTS.md](AGENTS.md)에는 프로젝트 구성과 문서 갱신 규칙이 있습니다.
