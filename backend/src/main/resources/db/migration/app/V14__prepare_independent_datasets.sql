ALTER TABLE dataset ADD COLUMN workspace_id VARCHAR(36);
ALTER TABLE dataset ADD CONSTRAINT fk_dataset_workspace FOREIGN KEY (workspace_id)
    REFERENCES workspace(workspace_id) ON DELETE NO ACTION ON UPDATE NO ACTION;
CREATE INDEX idx_dataset_workspace_created ON dataset(workspace_id, created_at DESC, dataset_id ASC);
-- V15 이관에서 각 공간의 독립 데이터셋 ID를 한 번 발급하기 위한 임시 컬럼입니다.
ALTER TABLE dataset_workspace ADD COLUMN copy_dataset_id VARCHAR(36);
