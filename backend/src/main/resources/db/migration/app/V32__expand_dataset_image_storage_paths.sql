ALTER TABLE dataset_image ALTER COLUMN storage_path VARCHAR(300) NOT NULL;
ALTER TABLE dataset_image ALTER COLUMN thumbnail_path VARCHAR(300);
ALTER TABLE dataset_image_history ALTER COLUMN storage_path VARCHAR(300) NOT NULL;
ALTER TABLE dataset_image_history ALTER COLUMN thumbnail_path VARCHAR(300);
