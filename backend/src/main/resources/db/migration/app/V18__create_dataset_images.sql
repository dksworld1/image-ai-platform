CREATE TABLE dataset_image (
    image_id VARCHAR(36) NOT NULL,
    dataset_id VARCHAR(36) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(100) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT '0',
    updated_by VARCHAR(100) NOT NULL DEFAULT '0',
    CONSTRAINT pk_dataset_image PRIMARY KEY (image_id),
    CONSTRAINT uq_dataset_image_filename UNIQUE (dataset_id, original_filename),
    CONSTRAINT uq_dataset_image_storage UNIQUE (storage_path),
    CONSTRAINT fk_dataset_image_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(dataset_id) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT ck_dataset_image_size CHECK (size_bytes > 0)
);
