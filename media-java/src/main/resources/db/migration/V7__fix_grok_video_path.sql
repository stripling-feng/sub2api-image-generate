UPDATE ai_models
SET generation_path = '/v1/videos',
    update_time = CURRENT_TIMESTAMP
WHERE model_type = 'VIDEO'
  AND model_key IN ('grok-video', 'grok-video-1.5')
  AND deleted = 0;
