CREATE TABLE IF NOT EXISTS ocr_image_translate_worker_configs (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    max_chinese_image_count INTEGER NOT NULL DEFAULT 5,
    batch_size INTEGER NOT NULL DEFAULT 1,
    poll_ms BIGINT NOT NULL DEFAULT 60000,
    model VARCHAR(128) DEFAULT 'gpt-image-2',
    quality VARCHAR(32) DEFAULT 'medium',
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ocr_image_translate_worker_configs_updated_at
    ON ocr_image_translate_worker_configs (updated_at);

CREATE TABLE IF NOT EXISTS ocr_image_translate_worker_logs (
    id BIGSERIAL PRIMARY KEY,
    spu_id BIGINT NOT NULL,
    product_id VARCHAR(128),
    ocr_task_id BIGINT,
    image_type INTEGER,
    source_field VARCHAR(64),
    source_index INTEGER,
    status VARCHAR(32) NOT NULL,
    model VARCHAR(128),
    original_url TEXT,
    translated_url TEXT,
    temu_url TEXT,
    message TEXT,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ocr_image_translate_worker_logs_spu
    ON ocr_image_translate_worker_logs (spu_id);

CREATE INDEX IF NOT EXISTS idx_ocr_image_translate_worker_logs_task
    ON ocr_image_translate_worker_logs (ocr_task_id);

CREATE INDEX IF NOT EXISTS idx_ocr_image_translate_worker_logs_status
    ON ocr_image_translate_worker_logs (status);

CREATE INDEX IF NOT EXISTS idx_ocr_image_translate_worker_logs_created_at
    ON ocr_image_translate_worker_logs (created_at);
