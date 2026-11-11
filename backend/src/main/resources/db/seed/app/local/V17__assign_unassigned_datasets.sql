-- 기존 local DB의 미귀속 데이터만 첫 번째 공통 공간으로 이관합니다.
-- 버전 DML은 반복 seed보다 먼저 실행되므로 필요한 초기 공간·admin 멤버십을 먼저 준비합니다.
INSERT INTO workspace (workspace_id, name, description, owner_user_key, status,
    created_at, updated_at, created_by, updated_by)
SELECT '00000000-0000-4000-8000-000000000001', '공통 워크스페이스', '로컬 개발용 공통 작업 공간',
    account.user_key, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM auth_user account
WHERE account.user_id = 'admin'
    AND EXISTS (SELECT 1 FROM dataset WHERE workspace_id IS NULL)
    AND NOT EXISTS (SELECT 1 FROM workspace WHERE workspace_id = '00000000-0000-4000-8000-000000000001');

INSERT INTO workspace_member (workspace_id, user_key, role_code, status,
    requested_at, reviewed_at, reviewed_by, joined_at, created_at, updated_at, created_by, updated_by)
SELECT space.workspace_id, account.user_key, 'ADMIN', 'ACTIVE',
    NULL, NULL, NULL, space.created_at, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0'
FROM workspace space JOIN auth_user account ON account.user_id = 'admin'
WHERE space.workspace_id = '00000000-0000-4000-8000-000000000001'
    AND EXISTS (SELECT 1 FROM dataset WHERE workspace_id IS NULL)
    AND NOT EXISTS (SELECT 1 FROM workspace_member member
        WHERE member.workspace_id = space.workspace_id AND member.user_key = account.user_key);

UPDATE dataset SET workspace_id = '00000000-0000-4000-8000-000000000001',
    updated_at = CURRENT_TIMESTAMP, updated_by = '0'
WHERE workspace_id IS NULL;
