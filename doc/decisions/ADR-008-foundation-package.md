# ADR-008: 공통 기반 패키지

- 상태: 채택
- 날짜: 2026-11-02
- 대체/관련 기록: ADR-006 백엔드 패키지 역할 경계, ADR-007 백엔드 모듈 경계

## 맥락

ADR-007에서 정한 `auth`와 `dataset` 기능 모듈을 두고 공통 기반 코드의 소유 위치가 남아 있었습니다. 기존 설정, 헬스체크와 데이터 기반 DAO를 기능 모듈 밖에 모아야 합니다. 애플리케이션 진입점은 컴포넌트 스캔의 루트 패키지에 계속 둡니다.

## 대안

- 공통 기반 코드를 `com.imageaiplatform` 바로 아래 기존 역할 패키지에 둡니다. 패키지 깊이는 얕지만 기능 모듈과 기반 코드의 소유 경계가 같은 수준에서 구분되지 않습니다.
- 공통 기반 코드를 `com.imageaiplatform.foundation` 아래 역할별 하위 패키지로 둡니다. 기능과 기반 책임을 분명히 구분할 수 있습니다.

## 결정

- 공통 설정은 `foundation.config`, 헬스체크는 `foundation.controller`, DAO 계약과 구현은 `foundation.dao` 및 `foundation.dao.impl`에 둡니다.
- 기존 `config`, `controller`, `dao` 소스 파일을 해당 `foundation` 패키지로 이동하고 패키지 선언과 참조를 갱신합니다.
- `Application`은 컴포넌트 스캔 범위를 유지하기 위해 `com.imageaiplatform`에 둡니다.
- 업무 기능 패키지는 ADR-007에 따라 `auth`, `dataset`으로 유지합니다.

## 결과

- 공통 기반과 업무 모듈의 소유 경계가 패키지에 드러납니다.
- ADR-006의 역할 분리는 기반 코드 내부의 `config`, `controller`, `dao` 역할에도 적용됩니다. ADR-007의 이번 변경 범위 제한은 본 결정에 따라 기반 코드 패키지 이동으로 구체화됩니다.
