ALTER TABLE dataset_image ALTER COLUMN original_filename VARCHAR(1024) NOT NULL;
ALTER TABLE dataset_image DROP CONSTRAINT uq_dataset_image_active_filename;
ALTER TABLE dataset_image DROP COLUMN active_filename;
ALTER TABLE dataset_image ADD COLUMN active_filename VARCHAR(1024)
    GENERATED ALWAYS AS (CASE WHEN is_deleted = FALSE THEN original_filename ELSE NULL END);
ALTER TABLE dataset_image ADD CONSTRAINT uq_dataset_image_active_filename UNIQUE (dataset_id, active_filename);
ALTER TABLE dataset_image_history ALTER COLUMN original_filename VARCHAR(1024) NOT NULL;
ALTER TABLE dataset_file ALTER COLUMN original_filename VARCHAR(1024) NOT NULL;
ALTER TABLE dataset_file DROP CONSTRAINT uq_dataset_file_active_filename;
ALTER TABLE dataset_file DROP COLUMN active_filename;
ALTER TABLE dataset_file ADD COLUMN active_filename VARCHAR(1024)
    GENERATED ALWAYS AS (CASE WHEN is_deleted = FALSE THEN original_filename ELSE NULL END);
ALTER TABLE dataset_file ADD CONSTRAINT uq_dataset_file_active_filename UNIQUE (dataset_id, active_filename);
ALTER TABLE dataset_file_history ALTER COLUMN original_filename VARCHAR(1024) NOT NULL;
