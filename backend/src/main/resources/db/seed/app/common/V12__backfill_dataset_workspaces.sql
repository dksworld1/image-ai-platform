-- 기존 단일 공간 귀속과 감사 정보를 연결 테이블로 보존합니다. 미귀속 데이터는 배정하지 않습니다.
INSERT INTO dataset_workspace (dataset_id, workspace_id, created_at, updated_at, created_by, updated_by)
SELECT dataset_id, workspace_id, created_at, updated_at, created_by, updated_by
FROM dataset WHERE workspace_id IS NOT NULL;
