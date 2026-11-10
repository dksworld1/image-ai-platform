-- Preserve legacy keys, role references and audit values. Real names remain unknown (NULL).
UPDATE auth_user SET user_id = user_key WHERE user_id IS NULL;
