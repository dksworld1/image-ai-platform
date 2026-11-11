-- common seed의 V12 데이터 이관 후 기존 단일 공간 컬럼을 제거합니다.
ALTER TABLE dataset DROP CONSTRAINT fk_dataset_workspace;
DROP INDEX idx_dataset_workspace_created;
ALTER TABLE dataset DROP COLUMN workspace_id;
