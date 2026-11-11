-- 공간 ID 오름차순 첫 공간은 기존 dataset_id를 보존하고 나머지는 새 UUID를 발급합니다.
UPDATE dataset_workspace linked SET copy_dataset_id =
    CASE WHEN workspace_id = (SELECT MIN(other.workspace_id) FROM dataset_workspace other
        WHERE other.dataset_id = linked.dataset_id)
    THEN dataset_id ELSE CAST(RANDOM_UUID() AS VARCHAR) END;

UPDATE dataset target SET workspace_id = (
    SELECT linked.workspace_id FROM dataset_workspace linked
    WHERE linked.dataset_id = target.dataset_id AND linked.copy_dataset_id = target.dataset_id
)
WHERE EXISTS (SELECT 1 FROM dataset_workspace linked WHERE linked.dataset_id = target.dataset_id);

-- 기존 데이터의 이름·설명·감사 값은 각 독립 데이터셋에도 보존합니다.
INSERT INTO dataset (dataset_id, name, description, created_at, updated_at, created_by, updated_by, workspace_id)
SELECT linked.copy_dataset_id, source.name, source.description,
    source.created_at, source.updated_at, source.created_by, source.updated_by, linked.workspace_id
FROM dataset_workspace linked JOIN dataset source ON source.dataset_id = linked.dataset_id
WHERE linked.copy_dataset_id <> linked.dataset_id;
