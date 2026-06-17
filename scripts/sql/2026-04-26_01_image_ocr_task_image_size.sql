ALTER TABLE image_ocr_task
    ADD COLUMN IF NOT EXISTS image_width INTEGER,
    ADD COLUMN IF NOT EXISTS image_height INTEGER,
    ADD COLUMN IF NOT EXISTS image_md5 VARCHAR(32),
    ADD COLUMN IF NOT EXISTS translate_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS translated_image_url VARCHAR(2000);

CREATE INDEX IF NOT EXISTS idx_ocr_task_image_size
    ON image_ocr_task (image_width, image_height);

CREATE INDEX IF NOT EXISTS idx_ocr_task_spu_image_md5
    ON image_ocr_task (spu_id, image_md5);

CREATE INDEX IF NOT EXISTS idx_ocr_task_translate_status
    ON image_ocr_task (translate_status);

CREATE TABLE IF NOT EXISTS image_ocr_size_filter_config (
    id BIGSERIAL PRIMARY KEY,
    image_width INTEGER NOT NULL,
    image_height INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ocr_size_filter_size UNIQUE (image_width, image_height)
);

CREATE INDEX IF NOT EXISTS idx_ocr_size_filter_size
    ON image_ocr_size_filter_config (image_width, image_height);

CREATE INDEX IF NOT EXISTS idx_ocr_size_filter_enabled
    ON image_ocr_size_filter_config (enabled);
