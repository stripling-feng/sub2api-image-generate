UPDATE ai_models
SET default_params = default_params - 'requiresVideo'
WHERE model_key IN ('omni-v2v', 'omni-v2v-no-water');
