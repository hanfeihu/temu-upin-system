ALTER TABLE temu_shops
    ADD COLUMN IF NOT EXISTS dianxiaomi_cookie TEXT;

ALTER TABLE temu_orders
    ADD COLUMN IF NOT EXISTS dianxiaomi_package_number VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_temu_orders_dianxiaomi_package_number
    ON temu_orders (dianxiaomi_package_number);
