UPDATE media_ai_models
SET parameter_schema = '[
  {"key":"size","label":"Image size","type":"select","default":"1:1","options":[
    {"label":"1:1","value":"1:1"},
    {"label":"3:2","value":"3:2"},
    {"label":"2:3","value":"2:3"},
    {"label":"auto","value":"auto"}
  ]}
]'::jsonb,
    default_params = '{}'::jsonb,
    supports_mask = 0
WHERE model_type = 'IMAGE'
  AND model_key = 'gpt-image-2';

UPDATE media_ai_models
SET parameter_schema = jsonb_build_array(
      jsonb_build_object(
        'key', 'aspect_ratio', 'label', 'Aspect ratio', 'type', 'select', 'default', '1:1',
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
        'key', 'size', 'label', 'Exact size', 'type', 'size',
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
        'key', 'quality', 'label', 'Quality', 'type', 'select',
        'options', '[
          {"label":"medium","value":"medium"},
          {"label":"low","value":"low"},
          {"label":"high","value":"high"}
        ]'::jsonb
      )
    ),
    default_params = '{}'::jsonb,
    supports_mask = 0
WHERE model_type = 'IMAGE'
  AND model_key IN ('gpt-image-2-1k', 'gpt-image-2-2k', 'gpt-image-2-4k');
