ALTER TABLE ai_models
    ADD COLUMN billing_mode varchar(20) NOT NULL DEFAULT 'PER_REQUEST'
        CHECK (billing_mode IN ('PER_REQUEST', 'PER_SECOND'));

CREATE TABLE video_generation_jobs (
    id varchar(64) PRIMARY KEY,
    profile_id text NOT NULL REFERENCES api_profiles(id) ON DELETE CASCADE,
    model_config_id bigint NOT NULL REFERENCES ai_models(id),
    request_id varchar(64) NOT NULL,
    prompt text NOT NULL,
    model varchar(100) NOT NULL,
    duration integer NOT NULL CHECK (duration BETWEEN 4 AND 15),
    aspect_ratio varchar(20) NOT NULL,
    resolution varchar(20) NOT NULL,
    generate_audio smallint NOT NULL DEFAULT 0 CHECK (generate_audio IN (0, 1)),
    params jsonb NOT NULL DEFAULT '{}'::jsonb,
    upstream_task_id varchar(255),
    upstream_status varchar(50),
    progress integer NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    error_message text,
    duration_ms integer,
    next_poll_at timestamp,
    poll_error_count integer NOT NULL DEFAULT 0,
    poll_lease_until timestamp,
    completed_at timestamp,
    billing_status varchar(30),
    billing_amount numeric(20,10),
    billing_api_key_id text,
    billing_user_id text,
    billing_account_id text,
    billing_usage_log_id text,
    billing_reserved_at timestamp,
    billing_settled_at timestamp,
    billing_error text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX video_jobs_profile_created_idx ON video_generation_jobs(profile_id, created_at DESC);
CREATE INDEX video_jobs_request_idx ON video_generation_jobs(request_id, created_at);
CREATE INDEX video_jobs_poll_idx ON video_generation_jobs(status, next_poll_at);
CREATE INDEX video_jobs_upstream_idx ON video_generation_jobs(upstream_task_id);

CREATE TABLE generated_videos (
    id varchar(64) PRIMARY KEY,
    job_id varchar(64) NOT NULL UNIQUE REFERENCES video_generation_jobs(id) ON DELETE CASCADE,
    public_url text NOT NULL,
    mime_type varchar(100) NOT NULL DEFAULT 'video/mp4',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX generated_videos_job_idx ON generated_videos(job_id);

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
SELECT provider.id, 'VIDEO', seed.model_key, seed.display_name, seed.upstream_model, seed.generation_path,
       1, 4, seed.max_images, 0, seed.parameter_schema::jsonb, seed.default_params::jsonb,
       0, 'PER_REQUEST', 0, seed.model_sort
FROM provider
CROSS JOIN (VALUES
    ('seedance-2.0', 'Seedance 2.0', 'seedance-2.0', '/v1/videos', 4,
     '[{"key":"duration","label":"视频时长","type":"select","default":8,"options":[4,5,6,7,8,9,10,11,12,13,14,15]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16","1:1","21:9","3:4","4:3"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["480p","720p"]},{"key":"generateAudio","label":"生成原生音频","type":"boolean","default":true}]',
     '{"protocol":"seedance","images":4,"videos":3,"audios":1,"frameInputs":true}', 1),
    ('seedance-2.0-fast', 'Seedance 2.0 Fast', 'seedance-2.0-fast', '/v1/videos', 4,
     '[{"key":"duration","label":"视频时长","type":"select","default":8,"options":[4,5,6,7,8,9,10,11,12,13,14,15]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16","1:1","21:9","3:4","4:3"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["480p","720p"]},{"key":"generateAudio","label":"生成原生音频","type":"boolean","default":true}]',
     '{"protocol":"seedance","images":4,"videos":3,"audios":1,"frameInputs":true}', 2),
    ('seedance-2.0-mini', 'Seedance 2.0 Mini', 'seedance-2.0-mini', '/v1/videos', 4,
     '[{"key":"duration","label":"视频时长","type":"select","default":8,"options":[4,5,6,7,8,9,10,11,12,13,14,15]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16","1:1","21:9","3:4","4:3"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["480p","720p"]},{"key":"generateAudio","label":"生成原生音频","type":"boolean","default":true}]',
     '{"protocol":"seedance","images":4,"videos":3,"audios":1,"frameInputs":true}', 3),
    ('grok-video', 'Grok Video', 'cy-gv1-grok-video', '/v1/video', 7,
     '[{"key":"duration","label":"视频时长","type":"select","default":6,"options":[4,6,8,10,12,15]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["1:1","16:9","9:16","4:3","3:4","3:2","2:3"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["480p","720p"]}]',
     '{"protocol":"grok","images":7,"videos":1,"audios":0,"frameInputs":false}', 4),
    ('grok-video-1.5', 'Grok Video 1.5', 'grok-video-1.5', '/v1/video', 1,
     '[{"key":"duration","label":"视频时长","type":"select","default":6,"options":[4,6,8,10,12,15]},{"key":"aspectRatio","label":"画面比例","type":"select","default":"16:9","options":["16:9","9:16"]},{"key":"resolution","label":"清晰度","type":"select","default":"720p","options":["480p","720p"]}]',
     '{"protocol":"grok","images":1,"videos":0,"audios":0,"frameInputs":false,"requiresImage":true}', 5)
) AS seed(model_key, display_name, upstream_model, generation_path, max_images, parameter_schema, default_params, model_sort)
ON CONFLICT(model_key) DO NOTHING;
