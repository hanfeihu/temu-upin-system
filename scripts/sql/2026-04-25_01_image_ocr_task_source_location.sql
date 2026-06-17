ALTER TABLE image_ocr_task
    ADD COLUMN IF NOT EXISTS source_field VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_index INTEGER;

CREATE INDEX IF NOT EXISTS idx_ocr_task_source_location
    ON image_ocr_task (spu_id, source_field, source_index);
