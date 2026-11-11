# 데이터셋 공유 구조 폐기 이력

- 폐기일: 2026-11-11
- 상태: V16에서 dataset_workspace 제거, 공간별 독립 생성으로 대체
- 결정: [ADR-034](../../decisions/ADR-034-independent-dataset-creation.md)

[이전 연결 테이블 정의](dataset-workspace.md)는 V11~V13 공유 구조의 이력입니다. 현재 구조와 API는 [dataset](../../db-schema/tables/dataset/dataset.md), [API 계약](../../api/api.md)을 따릅니다. V11~V13 SQL과 ADR-033은 과거 이력으로 유지합니다.
