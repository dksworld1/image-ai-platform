# ADR-006: 백엔드 패키지 역할 경계

- 상태: 채택
- 날짜: 2026-11-01
- 관련 단계: [1단계 백엔드 프레임워크와 데이터 기반](../stages/backend/stage-01-backend-foundation.md)

## 맥락

백엔드 기반이 마련된 시점에 이후 기능 구현에서도 일관되게 사용할 패키지 구조가 필요합니다. 역할 경계가 분명하면 HTTP 처리, 업무 흐름, 저장소 접근, 도메인 규칙이 서로 뒤섞이지 않습니다.

## 대안

- 모든 컴포넌트를 하나의 패키지에 둡니다.
- 기능별 패키지만 두고 각 기능 안에 역할을 구분합니다.
- 최상위 역할 패키지를 두고 각 역할 아래 기능별 하위 패키지를 둡니다.

## 결정

- `com.imageaiplatform.controller`, `service`, `dao`, `domain`을 백엔드의 최상위 역할 패키지로 사용합니다.
- DAO 계약 인터페이스는 `dao` 바로 아래에 두고 DAO 구현체는 `dao.impl`에 둡니다.
- `domain`에는 업무에 필요한 모델, 값 객체, 도메인 규칙 및 도메인 컴포넌트를 둡니다. HTTP나 저장소 기술 세부사항은 domain에 두지 않습니다.
- 필요한 업무가 아직 없는 경우 빈 패키지나 placeholder 클래스를 만들지 않습니다.
- 기존 기반 구성의 HealthController는 `controller`, 공통 기반 DAO 계약은 `dao`, 구현체는 `dao.impl`에 배치합니다.

## 결과

- 업무 흐름과 트랜잭션 경계는 service에 놓고, DAO를 service에서 명시적으로 선택합니다.
- API DTO는 controller 경계에 두며, domain 모델을 HTTP 응답으로 직접 노출하지 않습니다.
- 이 결정은 현재 [아키텍처 문서](../architecture/architecture.md)와 [1단계 문서](../stages/backend/stage-01-backend-foundation.md)에 반영합니다.
