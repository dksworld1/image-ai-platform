# 메뉴·화면 API — 제거됨

갱신일: 2026-11-09

[ADR-027](../decisions/ADR-027-remove-ui-module.md)에 따라 기존 UI 모듈을 철거했습니다. `/api/ui/navigation`, `/api/ui/screens` 및 하위 경로, `/api/ui/menus` 및 하위 경로는 더 이상 제공하지 않습니다. 기존 컨트롤러·서비스·DAO·도메인·MyBatis 매퍼와 전용 테스트도 제거했습니다.

로그인·서비스 권한 검사는 유지합니다. 새 메뉴 기능은 아직 구현하지 않았으며 메뉴 선택 URL은 React 코드에서 관리하고 메뉴 숨김은 렌더링 제어로 다룰 예정입니다. 메뉴 숨김으로 직접 URL 접근이나 업무 API 실행을 차단하지 않습니다.

DB 철거 순서와 실제 적용 제약은 [마이그레이션 운영 문서](../operations/database-migration.md)를 참고합니다. 기존 화면 구성은 [폐기된 스토리보드](../storyboards/screen-management/README.md)에 이력으로 보관합니다.
