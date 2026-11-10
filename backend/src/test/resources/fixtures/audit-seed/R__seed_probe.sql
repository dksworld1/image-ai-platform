INSERT INTO audit_test_probe (id, created_at, updated_at, created_by, updated_by)
SELECT 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
WHERE NOT EXISTS (SELECT 1 FROM audit_test_probe WHERE id = 7);
