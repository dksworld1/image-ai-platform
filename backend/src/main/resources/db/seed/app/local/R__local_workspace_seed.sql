-- local 전용 공통 작업 공간. R__local_app_seed.sql의 admin 생성 후 실행합니다.
-- 고정 UUID는 초기 데이터의 식별자이며 재실행 시 기존 공간·멤버십을 덮어쓰지 않습니다.
INSERT INTO workspace (workspace_id, name, description, owner_user_key, status,
    created_at, updated_at, created_by, updated_by)
SELECT seeded.workspace_id, seeded.name, '로컬 개발용 공통 작업 공간',
    account.user_key, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM (VALUES
    ('00000000-0000-4000-8000-000000000001', '공통 워크스페이스'),
    ('00000000-0000-4000-8000-000000000002', '공통 워크스페이스 2')
) seeded(workspace_id, name)
CROSS JOIN auth_user account
WHERE account.user_id = 'admin'
    AND NOT EXISTS (SELECT 1 FROM workspace WHERE workspace_id = seeded.workspace_id);

INSERT INTO workspace_member (workspace_id, user_key, role_code, status,
    requested_at, reviewed_at, reviewed_by, joined_at, created_at, updated_at, created_by, updated_by)
SELECT space.workspace_id, account.user_key, 'ADMIN', 'ACTIVE',
    NULL, NULL, NULL, space.created_at, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM workspace space
JOIN auth_user account ON account.user_id = 'admin'
WHERE space.workspace_id IN ('00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002')
    AND NOT EXISTS (SELECT 1 FROM workspace_member member
        WHERE member.workspace_id = space.workspace_id AND member.user_key = account.user_key);

-- 이미 초기화된 local DB도 목록·생성·삭제를 사용할 수 있게 기능 권한을 명시적으로 연결합니다.
INSERT INTO auth_role_permission (role_code, permission_code, created_at, updated_at, created_by, updated_by)
SELECT role.role_code, permission.permission_code, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM auth_role role
CROSS JOIN auth_permission permission
WHERE role.role_code = 'admin' AND permission.permission_code IN ('dataset:item:read', 'dataset:item:create', 'dataset:item:delete')
    AND NOT EXISTS (SELECT 1 FROM auth_role_permission assigned
        WHERE assigned.role_code = role.role_code AND assigned.permission_code = permission.permission_code);
