ALTER TABLE gpt_accounts
    ADD COLUMN IF NOT EXISTS used boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS gpt_accounts_used_idx
    ON gpt_accounts(used) WHERE deleted = 0;

COMMENT ON COLUMN gpt_accounts.used IS 'Whether this ChatGPT account has already been used';

DO $$
DECLARE
    account_menu_id bigint;
    update_permission_id bigint;
BEGIN
    SELECT id INTO account_menu_id FROM sys_menu
    WHERE path = 'gpt-accounts/account' AND deleted = 0 LIMIT 1;

    IF account_menu_id IS NOT NULL THEN
        SELECT id INTO update_permission_id FROM sys_menu
        WHERE parent_id = account_menu_id AND permission = 'gpt:account:update' AND deleted = 0 LIMIT 1;

        IF update_permission_id IS NULL THEN
            INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
            VALUES (account_menu_id, '标记GPT账号', 2, '', '', 'gpt:account:update', '', 5, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            RETURNING id INTO update_permission_id;
        END IF;

        INSERT INTO sys_role_menu(role_id, menu_id)
        SELECT id, update_permission_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
    END IF;
END $$;
