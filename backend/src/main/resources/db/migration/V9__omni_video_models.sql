WITH existing_provider AS (
    SELECT id FROM model_providers
    WHERE base_url = 'https://ai.cangyuansuanli.cn' AND deleted = 0
    ORDER BY id LIMIT 1
), inserted_provider AS (
    INSERT INTO model_providers(name, base_url, image_api_key, video_api_key, enabled, provider_sort)
    SELECT '沧元算力', 'https://ai.cangyuansuanli.cn', '', '', 1, 1
    WHERE NOT EXISTS (SELECT 1 FROM existing_provider)
    RETURNING id
), provider AS (
    SELECT id FROM existing_provider UNION ALL SELECT id FROM inserted_provider
)
INSERT INTO ai_models(provider_id, model_type, model_key, display_name, upstream_model, generation_path,
                      async_mode, max_count, max_reference_images, supports_mask, parameter_schema,
                      default_params, unit_price_usd, billing_mode, enabled, model_sort)
SELECT provider.id, 'VIDEO', seed.model_key, seed.display_name, seed.model_key, '/v1/videos',
       1, 4, seed.max_images, 0, seed.parameter_schema::jsonb, seed.default_params::jsonb,
       0, 'PER_REQUEST', 0, seed.model_sort
FROM provider
CROSS JOIN (VALUES
    ('omni-fast', 'Omni Fast', 5,
     '[{"key":"duration","label":"视频时长","type":"select","default":10,"options":[10]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["720p"]}]',
     '{"protocol":"omni","images":5,"videos":0,"audios":0,"frameInputs":true,"requiresVideo":false,"maxImageBytes":5242880}', 6),
    ('omni-fast-no-water', 'Omni Fast (No Watermark)', 5,
     '[{"key":"duration","label":"视频时长","type":"select","default":10,"options":[10]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["720p"]}]',
     '{"protocol":"omni","images":5,"videos":0,"audios":0,"frameInputs":true,"requiresVideo":false,"maxImageBytes":5242880}', 7),
    ('omni-v2v', 'Omni V2V', 2,
     '[{"key":"duration","label":"视频时长","type":"select","default":10,"options":[10]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["720p"]}]',
     '{"protocol":"omni","images":2,"videos":2,"audios":0,"frameInputs":false,"requiresVideo":true,"maxImageBytes":8388608,"maxVideoBytes":8388608}', 8),
    ('omni-v2v-no-water', 'Omni V2V (No Watermark)', 2,
     '[{"key":"duration","label":"视频时长","type":"select","default":10,"options":[10]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["720p"]}]',
     '{"protocol":"omni","images":2,"videos":2,"audios":0,"frameInputs":false,"requiresVideo":true,"maxImageBytes":8388608,"maxVideoBytes":8388608}', 9)
) AS seed(model_key, display_name, max_images, parameter_schema, default_params, model_sort)
ON CONFLICT(model_key) DO NOTHING;
