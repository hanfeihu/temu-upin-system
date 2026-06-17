CREATE TABLE IF NOT EXISTS ai_channel_business_config (
    id BIGSERIAL PRIMARY KEY,
    business_name VARCHAR(255) NOT NULL,
    business_code VARCHAR(100) NOT NULL,
    channel_id BIGINT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_channel_business_code
    ON ai_channel_business_config (business_code);

INSERT INTO ai_channels (name, platform, model, api_key, api_secret, base_url, enabled, description, sort_order, created_at)
SELECT
    'TMINOS Chatbot 文本',
    'openai_compatible',
    'gpt-5.5',
    'sk-gJcOn1tUeavYppjdDXIZiqmcdcZX1F4LwhKOz5TPj71TlsBi',
    NULL,
    'https://chatbot.tminos.com',
    TRUE,
    'OpenAI 兼容文本渠道',
    0,
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM ai_channels WHERE name = 'TMINOS Chatbot 文本'
);

INSERT INTO ai_channels (name, platform, model, api_key, api_secret, base_url, enabled, description, sort_order, created_at)
SELECT
    'TMINOS Chatbot 图片翻译',
    'openai_compatible_image',
    'gpt-image-1.5',
    'sk-gJcOn1tUeavYppjdDXIZiqmcdcZX1F4LwhKOz5TPj71TlsBi',
    NULL,
    'https://chatbot.tminos.com',
    TRUE,
    'OpenAI 兼容图片翻译渠道',
    1,
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM ai_channels WHERE name = 'TMINOS Chatbot 图片翻译'
);

WITH text_channel AS (
    SELECT id FROM ai_channels
    WHERE name IN ('TMINOS Chatbot 文本', 'TMINOS Chatbot')
    ORDER BY id
    LIMIT 1
)
INSERT INTO ai_channel_business_config (business_name, business_code, channel_id, enabled, description, created_at)
SELECT item.business_name, item.business_code, text_channel.id, TRUE, item.description, NOW()
FROM text_channel
CROSS JOIN (VALUES
    ('TEMU 属性 AI 填写', 'TEMU_ATTR_FILL', 'TEMU 属性模板自动填写'),
    ('TEMU 主销售规格推断', 'TEMU_MAIN_SALE_SPEC', 'TEMU SKU 主销售规格自动判断'),
    ('TEMU 标题优化', 'TEMU_TITLE_OPTIMIZE', 'TEMU 标题和类目关键词优化'),
    ('1688 选品 AI 报告', 'ALIBABA_1688_SELECTION_REPORT', '1688 选品报告分析'),
    ('图片提示词优化', 'PROMPT_OPTIMIZER', 'AI 生图提示词优化')
) AS item(business_name, business_code, description)
ON CONFLICT (business_code) DO UPDATE SET
    business_name = EXCLUDED.business_name,
    channel_id = EXCLUDED.channel_id,
    enabled = EXCLUDED.enabled,
    description = EXCLUDED.description,
    updated_at = NOW();

WITH image_channel AS (
    SELECT id FROM ai_channels
    WHERE name = 'TMINOS Chatbot 图片翻译'
    ORDER BY id
    LIMIT 1
)
INSERT INTO ai_channel_business_config (business_name, business_code, channel_id, enabled, description, created_at)
SELECT 'OCR 图片翻译', 'AI_IMAGE_TRANSLATE', image_channel.id, TRUE, 'OCR 中文图片翻译', NOW()
FROM image_channel
ON CONFLICT (business_code) DO UPDATE SET
    business_name = EXCLUDED.business_name,
    channel_id = EXCLUDED.channel_id,
    enabled = EXCLUDED.enabled,
    description = EXCLUDED.description,
    updated_at = NOW();
