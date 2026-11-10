ALTER TABLE auth_user RENAME COLUMN username TO user_key;
ALTER TABLE auth_user_role RENAME COLUMN username TO user_key;
ALTER TABLE auth_user ADD COLUMN user_id VARCHAR(100);
ALTER TABLE auth_user ADD COLUMN username VARCHAR(100);
ALTER TABLE auth_user DROP CONSTRAINT ck_auth_user_reserved_username;
ALTER TABLE auth_user ADD CONSTRAINT ck_auth_user_reserved_user_key CHECK (user_key <> '0');
