# 아키텍처

상태: 초안 기준선  
갱신일: 2026-11-01

## 개요

단일 저장소 안에 독립 실행 가능한 웹 클라이언트와 REST API를 둡니다. 현재 업무 기능은 정해지지 않아 최소한의 연결 확인 흐름만 구현합니다.

```text
브라우저 :4173 (정적 미리보기) 또는 :5173 (개발 서버)
    └── /api/* → Vite 프록시 → Spring Boot :8080
                               └── /api/health
```

## 구성 요소

- `frontend/`: 화면과 API 호출 모듈. 개발 및 정적 미리보기 중 `/api` 요청은 Vite 프록시를 통해 백엔드로 전달합니다.
- `backend/`: Java 21, Spring Boot 4, MVC 기반 API. Java 패키지는 `controller`, `service`, `dao`, `domain` 역할 경계로 나누고 기능별 하위 패키지를 둡니다.
- `doc/`: 제품 설계, 현재 아키텍처·API 계약, 기능 구현 단계 및 의사결정 기록.

## 로컬 실행 경계

- 웹 앱 기본 주소: `http://localhost:5173`
- 정적 미리보기 기본 주소: `http://localhost:4173`
- API 기본 주소: `http://localhost:8080`
- 개발 프록시 대상: `API_PROXY_TARGET` 환경 변수 또는 `http://localhost:8080`
- 정적 미리보기에서도 `/api` 요청을 같은 백엔드로 전달합니다.
- 브라우저에서 직접 API를 호출할 때 허용할 출처: `CORS_ALLOWED_ORIGIN` 또는 `http://localhost:5173`

백엔드는 `application.yaml` 및 프로파일별 YAML에서 데이터소스 설정을 가져오며 HikariCP와 MyBatis XML을 사용합니다. `application.yaml`의 `mybatis.config-location`과 `mybatis.mapper-locations`를 `MyBatisConfigurationProperties`로 바인딩해 각 세션 팩토리에 전달합니다. MyBatis 옵션은 `config/mybatis-config.xml`에서 읽습니다. 기본 프로파일은 `local`이고, `dev`와 `prod`는 `APP_CONFIG_FILE`에 설정 파일 경로를 지정합니다. `schema-foundation.sql`은 `local` 또는 `test` 프로파일에서만 catalog/archive 데이터소스에 실행되며 `prod`가 함께 활성화된 경우에도 실행되지 않습니다. 초기 SQL은 누락된 테이블만 만들고 기존 테이블과 데이터를 보존합니다. 프로파일별 Logback 설정 파일은 `backend/src/main/resources/config/logger/`에 둡니다. 업무 트랜잭션은 서비스 계층에 둡니다. 상세 설정과 미결정 운영 항목은 [1단계 백엔드 프레임워크 설계](../stages/stage-01-backend-foundation.md)를 참고합니다. 운영 DB 및 마이그레이션 전략, 운영 배포, 인증, 이미지 바이너리 저장소와 비동기 작업 큐는 별도 설계가 필요합니다.

프로파일별 로그 설정은 `backend/src/main/resources/config/logger/logback-{profile}.xml`에 두고 각 프로파일의 `logging.config`에서 classpath 경로로 참조합니다. 세부 로그 정책은 운영 요구에 따라 조정합니다.

다중 데이터소스는 라우팅 데이터소스 대신 `catalog`와 `archive` DB별 DAO Spring 빈으로 구성합니다. `catalogFoundationProbeDao`와 `archiveFoundationProbeDao`는 같은 `MyBatisFoundationProbeDao` 구현과 XML statement를 사용하지만 전용 `SqlSessionTemplate`·`SqlSessionFactory`·트랜잭션 매니저에 연결합니다. DAO는 `SqlSessionTemplate`으로 statement ID를 직접 호출하고 `@Mapper` 인터페이스/프록시는 사용하지 않습니다. 서비스는 `@Qualifier`로 DAO를 명시적으로 선택합니다. 두 DB 모두 H2 파일 모드이며 로컬과 테스트에서 분리된 파일을 사용합니다. 상세 설계는 [1단계 백엔드 프레임워크 설계](../stages/stage-01-backend-foundation.md)를 따릅니다.

## 확장 규칙

- 컨트롤러는 HTTP 요청/응답에 집중하고, 업무 규칙은 서비스 계층으로 둡니다.
- Java 패키지의 루트 역할은 `com.imageaiplatform.controller`, `com.imageaiplatform.service`, `com.imageaiplatform.dao`, `com.imageaiplatform.domain`입니다. DAO 계약 인터페이스는 `dao` 바로 아래에 두고 구현체는 `dao.impl`에 둡니다. `domain`에는 업무 모델, 값 객체, 도메인 규칙과 도메인 업무에 필요한 컴포넌트를 배치하며, 데이터 접근 세부사항은 두지 않습니다.
- 기존 기반 API도 이 규칙을 따릅니다. 상태 확인 컨트롤러는 `controller`, 공통 DAO 인터페이스는 `dao`, MyBatis 구현체는 `dao.impl`에 둡니다. 서비스나 업무 도메인이 아직 존재하지 않으면 빈 패키지나 임의의 클래스를 미리 만들지 않습니다.
- API 입력/출력은 명시적인 DTO로 정의하고 내부 도메인 모델을 그대로 노출하지 않습니다.
- 새 외부 의존성과 신뢰 경계는 관련 설계 문서 및 ADR에 기록합니다.
