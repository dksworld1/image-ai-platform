CREATE TABLE workspace (
    workspace_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    owner_user_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT '0',
    updated_by VARCHAR(100) NOT NULL DEFAULT '0',
    CONSTRAINT pk_workspace PRIMARY KEY (workspace_id),
    CONSTRAINT fk_workspace_owner FOREIGN KEY (owner_user_key) REFERENCES auth_user(user_key) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT ck_workspace_status CHECK (CASE status WHEN 'ACTIVE' THEN 1 WHEN 'ARCHIVED' THEN 1 ELSE 0 END = 1)
);

CREATE INDEX idx_workspace_owner ON workspace(owner_user_key);

CREATE TABLE workspace_member (
    workspace_id VARCHAR(36) NOT NULL,
    user_key VARCHAR(100) NOT NULL,
    role_code VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP(6) WITH TIME ZONE,
    reviewed_at TIMESTAMP(6) WITH TIME ZONE,
    reviewed_by VARCHAR(100),
    joined_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT '0',
    updated_by VARCHAR(100) NOT NULL DEFAULT '0',
    CONSTRAINT pk_workspace_member PRIMARY KEY (workspace_id, user_key),
    CONSTRAINT fk_workspace_member_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(workspace_id) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_workspace_member_user FOREIGN KEY (user_key) REFERENCES auth_user(user_key) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT ck_workspace_member_role CHECK (CASE role_code WHEN 'ADMIN' THEN 1 WHEN 'MEMBER' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_workspace_member_status CHECK (
        CASE status WHEN 'PENDING' THEN 1 WHEN 'ACTIVE' THEN 1 WHEN 'REJECTED' THEN 1
            WHEN 'CANCELLED' THEN 1 WHEN 'LEFT' THEN 1 WHEN 'REMOVED' THEN 1 ELSE 0 END = 1
    ),
    CONSTRAINT ck_workspace_member_review CHECK (
        (reviewed_at IS NULL AND reviewed_by IS NULL) OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
    ),
    CONSTRAINT ck_workspace_member_joined CHECK (
        CASE status WHEN 'ACTIVE' THEN joined_at IS NOT NULL WHEN 'LEFT' THEN joined_at IS NOT NULL
            WHEN 'REMOVED' THEN joined_at IS NOT NULL ELSE joined_at IS NULL END
    ),
    CONSTRAINT ck_workspace_member_requested CHECK (
        CASE status WHEN 'PENDING' THEN requested_at IS NOT NULL WHEN 'REJECTED' THEN requested_at IS NOT NULL
            WHEN 'CANCELLED' THEN requested_at IS NOT NULL ELSE TRUE END
    )
);

CREATE INDEX idx_workspace_member_user_status ON workspace_member(user_key, status, workspace_id);
CREATE INDEX idx_workspace_member_workspace_status ON workspace_member(workspace_id, status, requested_at, user_key);

-- 기존 데이터 귀속 및 생성 API 전환 전까지 nullable로 유지합니다.
ALTER TABLE dataset ADD COLUMN workspace_id VARCHAR(36);
ALTER TABLE dataset ADD CONSTRAINT fk_dataset_workspace FOREIGN KEY (workspace_id)
    REFERENCES workspace(workspace_id) ON DELETE NO ACTION ON UPDATE NO ACTION;
CREATE INDEX idx_dataset_workspace_created ON dataset(workspace_id, created_at DESC, dataset_id ASC);
