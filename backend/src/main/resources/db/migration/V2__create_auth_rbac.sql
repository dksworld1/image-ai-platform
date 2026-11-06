CREATE TABLE auth_role (
    role_code VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_auth_role PRIMARY KEY (role_code)
);

CREATE TABLE auth_permission (
    permission_code VARCHAR(150) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT pk_auth_permission PRIMARY KEY (permission_code)
);

CREATE TABLE auth_user_role (
    username VARCHAR(100) NOT NULL,
    role_code VARCHAR(100) NOT NULL,
    CONSTRAINT pk_auth_user_role PRIMARY KEY (username, role_code),
    CONSTRAINT fk_auth_user_role_user FOREIGN KEY (username)
        REFERENCES auth_user (username) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_auth_user_role_role FOREIGN KEY (role_code)
        REFERENCES auth_role (role_code) ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE INDEX idx_auth_user_role_role ON auth_user_role (role_code, username);

CREATE TABLE auth_role_permission (
    role_code VARCHAR(100) NOT NULL,
    permission_code VARCHAR(150) NOT NULL,
    CONSTRAINT pk_auth_role_permission PRIMARY KEY (role_code, permission_code),
    CONSTRAINT fk_auth_role_permission_role FOREIGN KEY (role_code)
        REFERENCES auth_role (role_code) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT fk_auth_role_permission_permission FOREIGN KEY (permission_code)
        REFERENCES auth_permission (permission_code) ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE INDEX idx_auth_role_permission_permission ON auth_role_permission (permission_code, role_code);
