-- Manual provisioning only: foundation_probe must already exist.
ALTER TABLE foundation_probe ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE foundation_probe ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE foundation_probe ADD COLUMN created_by VARCHAR(100) NOT NULL DEFAULT '0';
ALTER TABLE foundation_probe ADD COLUMN updated_by VARCHAR(100) NOT NULL DEFAULT '0';