ALTER TABLE dataset_image ADD COLUMN thumbnail_path VARCHAR(100);
ALTER TABLE dataset_image ADD CONSTRAINT uq_dataset_image_thumbnail UNIQUE (thumbnail_path);
