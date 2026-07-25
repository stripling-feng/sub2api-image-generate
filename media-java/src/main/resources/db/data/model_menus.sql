DO $$
DECLARE
    model_id bigint;
    image_id bigint;
    video_id bigint;
    provider_menu_id bigint;
BEGIN
    SELECT id INTO model_id FROM sys_menu
    WHERE parent_id = 0 AND menu_name = '模型管理' AND deleted = 0 LIMIT 1;
    IF model_id IS NULL THEN
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
        VALUES (0, '模型管理', 0, 'model', 'Layout', '', 'Box', 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        RETURNING id INTO model_id;
    END IF;
    SELECT id INTO image_id FROM sys_menu
    WHERE parent_id = model_id AND menu_name = '图片模型' AND deleted = 0 LIMIT 1;
    IF image_id IS NULL THEN
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
        VALUES (model_id, '图片模型', 1, 'model/image', 'views/model/ImageModelView.vue', '', 'Picture', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        RETURNING id INTO image_id;
    END IF;
    UPDATE sys_menu SET path = 'model/image', component = 'views/model/ImageModelView.vue', menu_sort = 2,
        update_time = CURRENT_TIMESTAMP WHERE id = image_id;

    SELECT id INTO video_id FROM sys_menu
    WHERE parent_id = model_id AND menu_name = '视频模型' AND deleted = 0 LIMIT 1;
    IF video_id IS NULL THEN
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
        VALUES (model_id, '视频模型', 1, 'model/video', 'views/model/ModelPlaceholderView.vue', '', 'VideoCamera', 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        RETURNING id INTO video_id;
    END IF;
    UPDATE sys_menu SET path = 'model/video', component = 'views/model/VideoModelView.vue', menu_sort = 3,
        update_time = CURRENT_TIMESTAMP WHERE id = video_id;

    SELECT id INTO provider_menu_id FROM sys_menu
    WHERE parent_id = model_id AND menu_name = '模型服务商' AND deleted = 0 LIMIT 1;
    IF provider_menu_id IS NULL THEN
        INSERT INTO sys_menu(parent_id, menu_name, menu_type, path, component, permission, icon, menu_sort, visible, create_time, update_time, deleted)
        VALUES (model_id, '模型服务商', 1, 'model/provider', 'views/model/ModelProviderView.vue', '', 'Connection', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        RETURNING id INTO provider_menu_id;
    END IF;

    INSERT INTO sys_role_menu(role_id, menu_id)
    SELECT id, model_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
    INSERT INTO sys_role_menu(role_id, menu_id)
    SELECT id, image_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
    INSERT INTO sys_role_menu(role_id, menu_id)
    SELECT id, video_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
    INSERT INTO sys_role_menu(role_id, menu_id)
    SELECT id, provider_menu_id FROM sys_role WHERE deleted = 0 ON CONFLICT DO NOTHING;
END $$;
