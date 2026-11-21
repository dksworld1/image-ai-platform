# ADR-051: 프로세스 내부 아티팩트 생명주기

- 상태: 채택
- 날짜: 2026-11-21
- 관련 결정: [ADR-049](ADR-049-artifact-file-unification.md), [ADR-050](ADR-050-artifact-storage-interface.md)

## 맥락

Artifact는 스테이지가 실행 결과로 생성한 파일을 담고 다음 스테이지에 입력으로 전달하는 단위입니다. 현재 설계는 스테이지 실행과 Artifact 생명주기 관리가 같은 프로세스에서 이루어진다고 가정합니다.

## 대안

- REST 엔드포인트 유지. 외부 호출을 지원하지만 같은 프로세스의 실행에는 HTTP 경계가 필요하지 않습니다.
- ArtifactService 직접 호출. 같은 프로세스에서 기존 상태 검증과 저장 트랜잭션을 재사용합니다.

## 결정

아티팩트 컨트롤러와 전용 HTTP 예외 처리를 제거합니다. ArtifactService는 기존 기능 권한·공간 멤버십 검사와 내부 사용자 키 기록을 계속 적용합니다. 파이프라인 포트는 확정 Artifact ID를 사용합니다.

## 결과

스토리지와 DB 계약은 그대로 사용합니다. 프로세스 간 실행이나 외부 조회·반입 요구가 생기면 필요한 HTTP 계약을 별도로 결정합니다. [이전 REST API](../archive/artifact-rest-api/README.md)
