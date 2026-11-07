INSERT INTO auth_role (role_code, display_name, description, system_role)
SELECT 'ADMIN', '관리자', '초기 관리자 역할', TRUE
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE role_code = 'ADMIN');

INSERT INTO auth_role (role_code, display_name, description, system_role)
SELECT 'MEMBER', '멤버', '초기 일반 사용자 역할', TRUE
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE role_code = 'MEMBER');

CREATE TABLE auth_audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_username VARCHAR(100) NOT NULL,
    target_username VARCHAR(100) NOT NULL,
    role_code VARCHAR(100) NOT NULL,
    action VARCHAR(30) NOT NULL,
    CONSTRAINT pk_auth_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_auth_audit_log_action CHECK (action IN ('ROLE_GRANTED', 'ROLE_REVOKED'))
);

CREATE INDEX idx_auth_audit_log_target ON auth_audit_log (target_username, occurred_at, id);
