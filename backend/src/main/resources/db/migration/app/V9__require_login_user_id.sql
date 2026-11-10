ALTER TABLE auth_user ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE auth_user ADD CONSTRAINT uq_auth_user_user_id UNIQUE (user_id);
