# 설계 문서 안내

이 디렉터리는 제품 설계와 기능 구현 단계에서 축적하는 개념, 원리, 결정 및 완료 조건을 보관합니다. 문서는 실제 구현 상태와 구분해 작성하고, 미확정 사항은 결정된 사실처럼 기록하지 않습니다.

## 문서 분류

- [제품 개요](product/project-overview.md): 제품 목적과 주요 기능
- [지원 포트폴리오 기록](portfolio/README.md): 채용공고 적합도 분석과 프로젝트 어필 근거
- [아키텍처](architecture/architecture.md): 현재 시스템 구성과 기술 경계
- [API 계약](api/api.md): 현재 제공하는 HTTP API와 변경 원칙
- [DB 테이블 정의서](db-schema/tables/README.md): 기능별 DB 테이블 구조 및 제약
- [화면·메뉴 스키마](db-schema/tables/ui/README.md): 화면 접근 정책·메뉴 계층·기존 권한 연결 (구현됨)
- [DB 마이그레이션 설정](operations/database-migration.md): DB별 Flyway 활성화, SQL·seed 경로와 생략 처리
- [개발 계획](plans/roadmap.md): 현재 상태와 다음 설계 항목
- [배포 및 서버 설정 운영](operations/deployment.md): 호스트 서버의 비밀정보 YAML 관리, 외부 설정 추가 및 JAR 실행·배포 절차
- [agent의 제안](agent-suggestions.md): 승인 전 추가 기능·개선안을 분류별 목록으로 기록
- [권한 시스템 설계 제안](proposals/authorization-design-proposal.md): RBAC, API 권한 식별자, 토큰·DB 조회 정책 및 강한 통제 논의
- [개발 진척도 현황판](stages/README.md): 전체 집계, 기능별 상태·완료 근거·남은 작업
- [프런트 단계 설계](stages/front/README.md): 화면 흐름, 코드 구성, API 연동 및 프런트 완료 조건
- [백엔드 단계 설계](stages/backend/README.md): 백엔드 기반·인증·업무 기능별 설계와 완료 조건
- [결정 기록](decisions/): 채택한 주요 기술·구조 결정 (ADR)
- [문서 서식](templates/): 설계 제안과 ADR 서식

## 기록 원칙

1. 기능 구현의 목표, 범위, 선행 조건, 핵심 개념과 완료 조건을 해당 단계 문서에 기록합니다.
2. 논의 중이거나 미확정인 내용은 `미결정`으로 표시하고, 채택한 결정은 근거와 영향까지 기록합니다.
3. 여러 단계에 영향을 주는 기술·구조 결정은 `decisions/`에 새 번호의 ADR로 남기고 관련 단계 문서에서 연결합니다.
4. 구현 상태는 실제 코드와 검증 결과를 확인한 뒤 갱신합니다. 실행하지 않은 검증을 완료로 기록하지 않습니다.
5. 아키텍처·API·제품 방향이 달라지면 해당 기준 문서와 영향을 받는 단계 문서를 함께 갱신합니다.
6. 권한 모듈의 정책·구조 설계와 보완 논의는 현재 [권한 시스템 설계 제안](proposals/authorization-design-proposal.md)에서 관리하고 추후 별도 설계 문서로 이관합니다. 스키마 정의는 [인증·권한 테이블 정의서](db-schema/tables/auth/README.md)에서 먼저 관리하며 제안서는 이를 링크합니다. 그 밖의 관련 문서는 제안서 링크와 현재 구현 상태·진행 상황을 기록하고 상세 설계는 중복 작성하지 않습니다.

문서는 한국어로 작성하고 코드, 명령어, 경로, API 식별자와 고유 기술명은 원문을 유지합니다.

단계 문서를 새로 만들 때는 [단계 기록 서식](templates/stage-record.md)을 사용합니다.
