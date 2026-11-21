# ADR-050: 아티팩트 스토리지 인터페이스

- 상태: 채택
- 날짜: 2026-11-21
- 관련 결정: [ADR-048](ADR-048-local-file-storage-module.md)

## 맥락

아티팩트 서비스에서 파일 저장 구현에 직접 의존하는 구조를 변경합니다.

## 대안

- 구체 클래스에 의존하면 현재 구현 흐름이 명확하지만 서비스와 로컬 파일 저장이 결합됩니다.
- 루트 키와 상대 경로 기반의 인터페이스를 사용하면 서비스의 확정 규칙과 스토리지 구현을 분리할 수 있습니다.

## 결정

ArtifactService는 ArtifactStorage를 주입받습니다. LocalFileStorage가 루트 키 해석과 파일 쓰기·읽기·실측·삭제를 구현합니다. 메타데이터 비교와 DB 롤백 보상은 ArtifactService가 담당합니다.

## 결과

기존 HTTP API와 app.storage.artifacts 설정, DB에 기록된 저장 위치는 그대로 사용합니다.
