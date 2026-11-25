ALTER TABLE dataset ADD COLUMN version_working_copy BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE dataset_image DROP CONSTRAINT uq_dataset_image_storage;
ALTER TABLE dataset_file DROP CONSTRAINT uq_dataset_file_storage;
ALTER TABLE dataset_image DROP CONSTRAINT uq_dataset_image_thumbnail;
ALTER TABLE dataset_image ADD CONSTRAINT uq_dataset_image_dataset_storage UNIQUE (dataset_id, storage_path);
ALTER TABLE dataset_image ADD CONSTRAINT uq_dataset_image_dataset_thumbnail UNIQUE (dataset_id, thumbnail_path);
ALTER TABLE dataset_file ADD CONSTRAINT uq_dataset_file_dataset_storage UNIQUE (dataset_id, storage_path);

ALTER TABLE dataset_version_draft RENAME TO dataset_version_draft_legacy;
CREATE TABLE dataset_version_draft (
    draft_id VARCHAR(36) CONSTRAINT pk_isolated_dataset_version_draft PRIMARY KEY,
    dataset_id VARCHAR(36) NOT NULL REFERENCES dataset(dataset_id) ON DELETE CASCADE,
    working_dataset_id VARCHAR(36) NOT NULL UNIQUE REFERENCES dataset(dataset_id),
    base_version_id VARCHAR(36) NOT NULL REFERENCES dataset_version(version_id),
    name VARCHAR(200) NOT NULL,
    change_note VARCHAR(2000),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL
);
CREATE INDEX ix_dataset_version_draft_dataset ON dataset_version_draft(dataset_id);
