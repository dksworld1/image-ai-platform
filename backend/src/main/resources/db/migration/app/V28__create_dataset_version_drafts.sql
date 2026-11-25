CREATE TABLE dataset_version_draft (
    dataset_id VARCHAR(36) PRIMARY KEY REFERENCES dataset(dataset_id) ON DELETE CASCADE,
    base_version_id VARCHAR(36) NOT NULL REFERENCES dataset_version(version_id),
    change_note VARCHAR(2000),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL
);
