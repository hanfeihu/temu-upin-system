CREATE TABLE IF NOT EXISTS alibaba_1688_selection_auto_push_configs (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    target_shop_ids_json TEXT,
    target_shop_names_json TEXT,
    batch_size INTEGER NOT NULL DEFAULT 1,
    poll_ms BIGINT NOT NULL DEFAULT 60000,
    force_create BOOLEAN NOT NULL DEFAULT FALSE,
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alibaba_1688_selection_auto_push_configs_updated_at
    ON alibaba_1688_selection_auto_push_configs (updated_at);

CREATE TABLE IF NOT EXISTS alibaba_1688_selection_auto_push_logs (
    id BIGSERIAL PRIMARY KEY,
    pool_id BIGINT,
    offer_id VARCHAR(64),
    product_collection_id BIGINT,
    status VARCHAR(32) NOT NULL,
    target_shop_ids_json TEXT,
    target_shop_names_json TEXT,
    message TEXT,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alibaba_1688_selection_auto_push_logs_pool
    ON alibaba_1688_selection_auto_push_logs (pool_id);

CREATE INDEX IF NOT EXISTS idx_alibaba_1688_selection_auto_push_logs_status
    ON alibaba_1688_selection_auto_push_logs (status);

CREATE INDEX IF NOT EXISTS idx_alibaba_1688_selection_auto_push_logs_created_at
    ON alibaba_1688_selection_auto_push_logs (created_at);
