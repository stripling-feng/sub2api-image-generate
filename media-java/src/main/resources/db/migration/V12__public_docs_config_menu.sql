DO $$
DECLARE
    system_id bigint;
    docs_id bigint;
    docs_list_id bigint;
    docs_edit_id bigint;
BEGIN
    SELECT id INTO system_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'system' AND deleted = 0 LIMIT 1;

    IF system_id IS NULL THEN
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
        VALUES (0, '系统管理', 0, 'system', 'Layout', '', 'system', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        RETURNING id INTO system_id;
    END IF;

    SELECT id INTO docs_id FROM sys_menu
    WHERE parent_id = system_id AND path = 'system/docs-config' AND deleted = 0 LIMIT 1;

    IF docs_id IS NULL THEN
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
        VALUES (system_id, '前台文档配置', 1, 'system/docs-config', 'views/system/PublicDocsConfigView.vue', 'system:docs:list', 'doc', 95, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        RETURNING id INTO docs_id;
    ELSE
        UPDATE sys_menu SET menu_name = '前台文档配置',
            component = 'views/system/PublicDocsConfigView.vue',
            permission = 'system:docs:list',
            icon = 'doc',
            visible = 1,
            update_time = CURRENT_TIMESTAMP
        WHERE id = docs_id;
    END IF;

    SELECT id INTO docs_list_id FROM sys_menu
    WHERE parent_id = docs_id AND permission = 'system:docs:list' AND deleted = 0 LIMIT 1;
    IF docs_list_id IS NULL THEN
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
        VALUES (docs_id, '查询前台文档配置', 2, '', '', 'system:docs:list', '', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        RETURNING id INTO docs_list_id;
    END IF;

    SELECT id INTO docs_edit_id FROM sys_menu
    WHERE parent_id = docs_id AND permission = 'system:docs:edit' AND deleted = 0 LIMIT 1;
    IF docs_edit_id IS NULL THEN
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
        VALUES (docs_id, '保存前台文档配置', 2, '', '', 'system:docs:edit', '', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        RETURNING id INTO docs_edit_id;
    END IF;

    INSERT INTO sys_role_menu(role_id, menu_id)
    SELECT id, system_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
    INSERT INTO sys_role_menu(role_id, menu_id)
    SELECT id, docs_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
    INSERT INTO sys_role_menu(role_id, menu_id)
    SELECT id, docs_list_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
    INSERT INTO sys_role_menu(role_id, menu_id)
    SELECT id, docs_edit_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
END $$;
