INSERT INTO dataset_image_history (
    history_id, image_id, dataset_id, action, version_count, previous_image_id,
    original_filename, storage_path, thumbnail_path, media_type, size_bytes,
    image_created_at, image_updated_at, image_created_by, image_updated_by,
    created_at, updated_at, created_by, updated_by)
SELECT image.image_id, image.image_id, image.dataset_id, 'BASELINE', image.version_count, image.previous_image_id,
    image.original_filename, image.storage_path, image.thumbnail_path, image.media_type, image.size_bytes,
    image.created_at, image.updated_at, image.created_by, image.updated_by,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM dataset_image image
WHERE NOT EXISTS (SELECT 1 FROM dataset_image_history history WHERE history.image_id = image.image_id);
