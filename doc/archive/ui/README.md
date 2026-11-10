# 폐기된 화면·메뉴 모듈 이력

- 상태: 폐기 이력
- 철거 결정일: 2026-11-09
- 문서 통합일: 2026-11-10
- 관련 결정: [ADR-027](../../decisions/ADR-027-remove-ui-module.md)

기존 UI 모듈의 API·테이블·화면 설계를 한곳에 보존합니다. 아래 문서의 구현 상태·계약·검증 내용은 철거 전 이력이며 현재 제공 기능으로 사용하지 않습니다. 새 화면·메뉴 구현의 확정 설계도 아닙니다.

| 문서 | 내용 |
|---|---|
| [API 철거 안내](api.md) | 제거된 경로와 현재 적용 제약 |
| [테이블 정의 이력](tables/README.md) | ui_screen·ui_menu·ui_screen_permission의 과거 구조 |
| [화면 스토리보드](storyboard/README.md) | 폐기된 화면 관리 설계와 원본 이미지 |

현재 시스템 계약은 [API](../../api/api.md)·[아키텍처](../../architecture/architecture.md)·[현재 테이블 정의](../../db-schema/tables/README.md)를 참고합니다. 실제 DB 철거·배포 기록은 [마이그레이션 운영 문서](../../operations/database-migration.md)에서 관리합니다.
