# 데이터셋 테이블

상태: 공간별 독립 생성·목록·삭제·이미지 및 데이터 파일 관리 구현. 실제 적용은 운영 기록 참조
갱신일: 2026-11-13

| 테이블 | 책임 | 적용 |
|---|---|---|
| [dataset](dataset.md) | 데이터셋 기본 정보와 생성·최종 수정 정보 | app V6 |
| [dataset_image](dataset-image.md) | 원본 이미지명·로컬 파일 상대 경로·형식·크기·공통 감사 정보 | app V18 |
| [dataset_image_history](dataset-image-history.md) | 생성·교체·삭제 이벤트, 버전별 파일·감사 스냅샷 | app V20·공통 seed V21 |
| [dataset_file / dataset_file_history](dataset-file.md) | 데이터 파일 원본 메타데이터·버전·설명과 작업 이력 | app V23·V24·V25 |

현재(ADR-034)는 dataset의 단일 workspace_id FK를 사용합니다. 공유 연결은 V16에서 제거했습니다. 목록은 가입 승인된 활성 공간을 기준으로 공간별 독립 데이터셋을 조회합니다. 생성자 컬럼은 감사 값이며 소유권으로 해석하지 않습니다. 생성자와 활성 멤버십을 함께 검사하는 쓰기 권한 규칙은 API 계약을 따릅니다. 이미지와 데이터 파일은 별도 테이블·권한으로 관리합니다.

## 폐기 이력

- [공유 연결 테이블](../../../archive/dataset-sharing/README.md): V11~V13의 dataset_workspace를 V16에서 제거. ADR-034로 공간별 독립 생성 채택.
