CREATE TABLE artifact (
    artifact_id VARCHAR(36) NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(workspace_id),
    data_type VARCHAR(100) NOT NULL,
    schema_name VARCHAR(200),
    schema_version VARCHAR(100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('WRITING', 'AVAILABLE', 'FAILED')),
    description VARCHAR(2000),
    retention_until TIMESTAMP(6) WITH TIME ZONE,
    protected_flag BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT ck_artifact_schema CHECK ((schema_name IS NULL AND schema_version IS NULL) OR (schema_name IS NOT NULL AND schema_version IS NOT NULL))
);
CREATE INDEX ix_artifact_workspace ON artifact(workspace_id, created_at);
CREATE TABLE artifact_file (
    file_id VARCHAR(36) NOT NULL PRIMARY KEY,
    artifact_id VARCHAR(36) NOT NULL REFERENCES artifact(artifact_id),
    internal_name VARCHAR(1024) NOT NULL,
    root_key VARCHAR(100) NOT NULL,
    relative_path VARCHAR(200) NOT NULL,
    media_type VARCHAR(200) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    checksum_algorithm VARCHAR(20) NOT NULL CHECK (checksum_algorithm = 'SHA-256'),
    checksum VARCHAR(64) NOT NULL,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT uq_artifact_file_name UNIQUE (artifact_id, internal_name),
    CONSTRAINT uq_artifact_file_path UNIQUE (root_key, relative_path)
);
CREATE TABLE artifact_set (
    set_id VARCHAR(36) NOT NULL PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL REFERENCES workspace(workspace_id),
    name VARCHAR(200) NOT NULL,
    purpose VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL
);
CREATE INDEX ix_artifact_set_workspace ON artifact_set(workspace_id, created_at);
CREATE TABLE artifact_set_version (
    version_id VARCHAR(36) NOT NULL PRIMARY KEY,
    set_id VARCHAR(36) NOT NULL REFERENCES artifact_set(set_id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED')),
    item_count BIGINT CHECK (item_count >= 0),
    composition_checksum VARCHAR(64),
    confirmed_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT uq_artifact_set_version UNIQUE (set_id, version_number),
    CONSTRAINT ck_artifact_set_confirmed CHECK (status != 'CONFIRMED' OR (item_count IS NOT NULL AND composition_checksum IS NOT NULL AND confirmed_at IS NOT NULL))
);
CREATE TABLE artifact_set_member (
    version_id VARCHAR(36) NOT NULL REFERENCES artifact_set_version(version_id),
    artifact_id VARCHAR(36) NOT NULL REFERENCES artifact(artifact_id),
    ordinal BIGINT NOT NULL CHECK (ordinal >= 0),
    source_key VARCHAR(1024),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    PRIMARY KEY (version_id, artifact_id),
    CONSTRAINT uq_artifact_set_member_order UNIQUE (version_id, ordinal)
);
