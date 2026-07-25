UPDATE ai_models
SET upstream_model = 'grok-video-1.5'
WHERE model_type = 'VIDEO'
  AND model_key = 'grok-video-1.5'
  AND upstream_model = 'cy-gv1-grok-video-1.5';
