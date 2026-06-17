ALTER TABLE temu_shops
    ADD COLUMN IF NOT EXISTS dianxiaomi_shop_id VARCHAR(64);

UPDATE temu_shops
SET dianxiaomi_shop_id = '8244811'
WHERE shop_name = '2店'
  AND (dianxiaomi_shop_id IS NULL OR btrim(dianxiaomi_shop_id) = '');

UPDATE temu_shops
SET shop_id = '634418227683684',
    dianxiaomi_shop_id = '8244765'
WHERE shop_name = '4店'
  AND (dianxiaomi_shop_id IS NULL OR btrim(dianxiaomi_shop_id) = '');

UPDATE temu_shops
SET dianxiaomi_shop_id = '8328855'
WHERE shop_name = '9店'
  AND (dianxiaomi_shop_id IS NULL OR btrim(dianxiaomi_shop_id) = '');

UPDATE temu_shops
SET dianxiaomi_shop_id = '8244308'
WHERE shop_name = 'ayshop'
  AND (dianxiaomi_shop_id IS NULL OR btrim(dianxiaomi_shop_id) = '');
