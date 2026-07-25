ALTER TABLE ai_models
    ADD COLUMN unit_price_usd numeric(20,10) NOT NULL DEFAULT 0.5,
    ADD CONSTRAINT ai_models_unit_price_usd_check CHECK (unit_price_usd >= 0);

UPDATE ai_models
SET parameter_schema = '[
  {"key":"size","label":"图片画幅","type":"select","default":"1:1","options":[
    {"label":"1:1","value":"1:1"},{"label":"3:2","value":"3:2"},
    {"label":"2:3","value":"2:3"},{"label":"自动","value":"auto"}
  ]}
]'::jsonb,
    default_params = '{"async":true,"stream":false,"response_format":"url"}'::jsonb,
    max_count = 10,
    max_reference_images = 9,
    supports_mask = 0
WHERE model_key = 'gpt-image-2';

UPDATE ai_models
SET parameter_schema = jsonb_build_array(
      jsonb_build_object(
        'key', 'aspect_ratio', 'label', '图片画幅', 'type', 'select', 'default', '1:1',
        'options', '[
          {"label":"1:1","value":"1:1"},{"label":"5:4","value":"5:4"},
          {"label":"7:6","value":"7:6"},{"label":"9:16","value":"9:16"},
          {"label":"21:9","value":"21:9"},{"label":"16:9","value":"16:9"},
          {"label":"3:2","value":"3:2"},{"label":"4:3","value":"4:3"},
          {"label":"4:5","value":"4:5"},{"label":"3:4","value":"3:4"},
          {"label":"2:3","value":"2:3"}
        ]'::jsonb
      ),
      jsonb_build_object(
        'key', 'size', 'label', '自定义尺寸', 'type', 'size',
        'placeholder', CASE model_key
          WHEN 'gpt-image-2-1k' THEN '1024x1024'
          WHEN 'gpt-image-2-2k' THEN '2048x2048'
          ELSE '3840x2160'
        END,
        'minPixels', 655360,
        'maxPixels', CASE model_key
          WHEN 'gpt-image-2-1k' THEN 1048576
          WHEN 'gpt-image-2-2k' THEN 4194304
          ELSE 8294400
        END,
        'maxSide', 3840, 'multiple', 16, 'maxRatio', 3
      ),
      jsonb_build_object(
        'key', 'quality', 'label', '质量', 'type', 'select', 'default', 'medium',
        'options', '[
          {"label":"中（默认）","value":"medium"},
          {"label":"低","value":"low"},
          {"label":"高","value":"high"}
        ]'::jsonb
      )
    ),
    default_params = '{"async":true,"stream":false,"response_format":"url"}'::jsonb,
    max_count = 1,
    max_reference_images = 9,
    supports_mask = 1
WHERE model_key IN ('gpt-image-2-1k', 'gpt-image-2-2k', 'gpt-image-2-4k');
