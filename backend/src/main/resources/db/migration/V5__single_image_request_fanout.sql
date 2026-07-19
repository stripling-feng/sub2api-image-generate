UPDATE ai_models
SET max_count = 10,
    update_time = CURRENT_TIMESTAMP
WHERE model_type = 'IMAGE'
  AND deleted = 0;
