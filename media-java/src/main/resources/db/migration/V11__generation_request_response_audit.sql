ALTER TABLE generation_jobs
    ADD COLUMN raw_request jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN raw_responses jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE video_generation_jobs
    ADD COLUMN raw_request jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN raw_responses jsonb NOT NULL DEFAULT '[]'::jsonb;
