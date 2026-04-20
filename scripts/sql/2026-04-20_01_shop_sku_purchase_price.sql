CREATE TABLE IF NOT EXISTS temu_shop_sku_purchase_price (
    id BIGSERIAL PRIMARY KEY,
    shop_id VARCHAR(64) NOT NULL,
    product_skc_id BIGINT,
    product_sku_id BIGINT NOT NULL,
    sku_ext_code VARCHAR(256),
    purchase_price INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_temu_shop_sku_purchase_price
    ON temu_shop_sku_purchase_price (shop_id, product_sku_id);

CREATE INDEX IF NOT EXISTS idx_temu_shop_sku_purchase_price_skc
    ON temu_shop_sku_purchase_price (shop_id, product_skc_id);

CREATE INDEX IF NOT EXISTS idx_temu_shop_sku_purchase_price_ext_code
    ON temu_shop_sku_purchase_price (shop_id, sku_ext_code);
