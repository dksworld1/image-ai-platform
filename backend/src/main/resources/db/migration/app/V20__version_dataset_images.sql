ALTER TABLE dataset_image DROP CONSTRAINT uq_dataset_image_filename;
ALTER TABLE dataset_image ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE dataset_image ADD COLUMN version_count INTEGER NOT NULL DEFAULT 1;
ALTER TABLE dataset_image ADD COLUMN previous_image_id VARCHAR(36);
ALTER TABLE dataset_image ADD COLUMN active_filename VARCHAR(255)
    GENERATED ALWAYS AS (CASE WHEN is_deleted = FALSE THEN original_filename ELSE NULL END);
ALTER TABLE dataset_image ADD CONSTRAINT uq_dataset_image_active_filename UNIQUE (dataset_id, active_filename);
ALTER TABLE dataset_image ADD CONSTRAINT ck_dataset_image_version CHECK (version_count > 0);

CREATE TABLE dataset_image_history (
    history_id VARCHAR(36) NOT NULL,
    image_id VARCHAR(36) NOT NULL,
    dataset_id VARCHAR(36) NOT NULL,
    action VARCHAR(10) NOT NULL,
    version_count INTEGER NOT NULL,
    previous_image_id VARCHAR(36),
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(100) NOT NULL,
    thumbnail_path VARCHAR(100),
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    image_created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    image_updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    image_created_by VARCHAR(100) NOT NULL,
    image_updated_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT '0',
    updated_by VARCHAR(100) NOT NULL DEFAULT '0',
    CONSTRAINT pk_dataset_image_history PRIMARY KEY (history_id),
    CONSTRAINT ck_dataset_image_history_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'BASELINE')),
    CONSTRAINT ck_dataset_image_history_version CHECK (version_count > 0)
);
CREATE INDEX ix_dataset_image_history_image ON dataset_image_history (image_id, created_at);
CREATE INDEX ix_dataset_image_history_dataset ON dataset_image_history (dataset_id, original_filename, version_count);
