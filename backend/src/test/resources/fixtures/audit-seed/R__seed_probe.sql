INSERT INTO audit_test_probe (id)
SELECT 7 WHERE NOT EXISTS (SELECT 1 FROM audit_test_probe WHERE id = 7);
