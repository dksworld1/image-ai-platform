INSERT INTO dataset_version_draft (draft_id, dataset_id, working_dataset_id, base_version_id, name, change_note,
    created_at, updated_at, created_by, updated_by)
SELECT d.dataset_id, d.dataset_id, d.dataset_id, d.base_version_id, 'v' || v.version_number || ' 편집본',
    d.change_note, d.created_at, d.updated_at, d.created_by, d.updated_by
FROM dataset_version_draft_legacy d JOIN dataset_version v ON v.version_id = d.base_version_id;
