CREATE TABLE dataset_workspace (
    dataset_id VARCHAR(36) NOT NULL,
    workspace_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT '0',
    updated_by VARCHAR(100) NOT NULL DEFAULT '0',
    CONSTRAINT pk_dataset_workspace PRIMARY KEY (dataset_id, workspace_id),
    CONSTRAINT fk_dataset_workspace_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(dataset_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_dataset_workspace_space FOREIGN KEY (workspace_id) REFERENCES workspace(workspace_id) ON DELETE NO ACTION ON UPDATE NO ACTION
);
CREATE INDEX idx_dataset_workspace_space ON dataset_workspace(workspace_id, dataset_id);
