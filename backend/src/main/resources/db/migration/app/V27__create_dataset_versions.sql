CREATE TABLE dataset_version (
    version_id VARCHAR(36) NOT NULL PRIMARY KEY,
    dataset_id VARCHAR(36) NOT NULL REFERENCES dataset(dataset_id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    change_note VARCHAR(2000),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT uq_dataset_version_number UNIQUE (dataset_id, version_number)
);
CREATE TABLE dataset_version_image (
    version_id VARCHAR(36) NOT NULL REFERENCES dataset_version(version_id),
    image_id VARCHAR(36) NOT NULL REFERENCES dataset_image(image_id),
    original_filename VARCHAR(1024) NOT NULL,
    description VARCHAR(2000),
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    version_count INTEGER NOT NULL CHECK (version_count > 0),
    source_created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    source_created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    PRIMARY KEY (version_id, image_id)
);
CREATE INDEX ix_dataset_version_image_source ON dataset_version_image(image_id);
CREATE TABLE dataset_version_file (
    version_id VARCHAR(36) NOT NULL REFERENCES dataset_version(version_id),
    file_id VARCHAR(36) NOT NULL REFERENCES dataset_file(file_id),
    original_filename VARCHAR(1024) NOT NULL,
    description VARCHAR(2000),
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    version_count INTEGER NOT NULL CHECK (version_count > 0),
    source_created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    source_created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    PRIMARY KEY (version_id, file_id)
);
CREATE INDEX ix_dataset_version_file_source ON dataset_version_file(file_id);
