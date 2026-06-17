WITH text_channel AS (
    SELECT id FROM ai_channels
    WHERE name IN ('TMINOS Chatbot', 'TMINOS Chatbot 文本')
      AND model = 'gpt-5.5'
    ORDER BY CASE WHEN name = 'TMINOS Chatbot' THEN 0 ELSE 1 END, id
    LIMIT 1
)
INSERT INTO ai_channel_business_config (business_name, business_code, channel_id, enabled, description, created_at)
SELECT 'TEMU SKU 规格翻译', 'TEMU_SKU_SPEC_TRANSLATE', text_channel.id, TRUE, 'TEMU 发布 SKU 规格值英文化', NOW()
FROM text_channel
ON CONFLICT (business_code) DO UPDATE SET
    business_name = EXCLUDED.business_name,
    channel_id = EXCLUDED.channel_id,
    enabled = EXCLUDED.enabled,
    description = EXCLUDED.description,
    updated_at = NOW();
