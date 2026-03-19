-- 创建渠道表并插入初始数据

-- 如果表已存在，先删除（可选）
-- DROP TABLE IF EXISTS ai_channels;

-- 创建渠道表
CREATE TABLE ai_channels (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    platform VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    api_key VARCHAR(500),
    api_secret VARCHAR(500),
    base_url VARCHAR(500),
    enabled BOOLEAN DEFAULT true,
    description VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- 插入初始渠道数据
INSERT INTO ai_channels (name, platform, model, api_key, api_secret, base_url, enabled, description, sort_order, created_at)
VALUES 
('Stability AI (SD3)', 'stability', 'sd3', '', '', 'https://api.stability.ai', true, 'Stability AI SD3 文生图', 1, NOW()),
('豆包-动漫风', 'volcengine', 'img2img_cartoon_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-动漫风格化', 2, NOW()),
('豆包-3D风', 'volcengine', 'img2img_disney_3d_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-3D风格化', 3, NOW()),
('豆包-写实风', 'volcengine', 'img2img_real_mix_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-写实风格化', 4, NOW()),
('豆包-日漫风', 'volcengine', 'img2img_makoto_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-日漫风格化', 5, NOW()),
('豆包-梦幻风', 'volcengine', 'img2img_blueline_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-梦幻风格化', 6, NOW()),
('豆包-水墨风', 'volcengine', 'img2img_water_ink_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-水墨风格化', 7, NOW()),
('豆包-陶瓷娃娃', 'volcengine', 'img2img_ceramics_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-陶瓷娃娃风格', 8, NOW()),
('豆包-中国红', 'volcengine', 'img2img_chinese_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-中国红风格', 9, NOW()),
('豆包-丑萌粘土', 'volcengine', 'img2img_clay_style', '', '', 'https://visual.volcengineapi.com', true, '火山引擎-丑萌粘土风格', 10, NOW()),
('即梦-图生图3.0', 'jimeng_i2i', 'jimeng_i2i_v30', '', '', 'https://visual.volcengineapi.com', true, '即梦图生图3.0智能参考', 11, NOW()),
('即梦-文生图3.1', 'jimeng_t2i', 'jimeng_t2i_v31', '', '', 'https://visual.volcengineapi.com', true, '即梦文生图3.1', 12, NOW());

-- 验证数据
SELECT * FROM ai_channels ORDER BY sort_order;
