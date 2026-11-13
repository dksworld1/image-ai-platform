# 데이터셋 테이블

상태: 목록·다중 공간 생성 구현, 실제 DB 미적용
갱신일: 2026-11-11

| 테이블 | 책임 | 적용 |
|---|---|---|
| [dataset](dataset.md) | 데이터셋 기본 정보와 생성·최종 수정 정보 | app V6 |
| [dataset_image](dataset-image.md) | 원본 이미지명·로컬 파일 상대 경로·형식·크기·공통 감사 정보 | app V18 |
| [dataset_image_history](dataset-image-history.md) | 생성·교체·삭제 이벤트, 버전별 파일·감사 스냅샷 | app V20·공통 seed V21 |

현재(ADR-034)는 dataset의 단일 workspace_id FK를 사용합니다. 공유 연결은 V16에서 제거했습니다. 목록은 가입 승인된 활성 공간을 기준으로 공간별 독립 데이터셋을 조회합니다. 생성자 컬럼은 감사 값이며 소유권으로 해석하지 않습니다. 이미지 관계·상세·수정·삭제 정책과 관리 화면은 후속 작업입니다.

## 폐기 이력

- [공유 연결 테이블](../../../archive/dataset-sharing/README.md): V11~V13의 dataset_workspace를 V16에서 제거. ADR-034로 공간별 독립 생성 채택.
