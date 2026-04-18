alter table if exists temu_shops
    add column if not exists site_id integer,
    add column if not exists warehouse_id varchar(128),
    add column if not exists sku_default_stock integer,
    add column if not exists sku_max_stock integer,
    add column if not exists origin_region1_short_name varchar(32),
    add column if not exists origin_region2_id bigint,
    add column if not exists freight_template_id varchar(128),
    add column if not exists shipment_limit_second integer;

alter table if exists temu_shops
    alter column site_id set default 100,
    alter column warehouse_id set default 'WH-03304781516934009',
    alter column sku_default_stock set default 1000,
    alter column sku_max_stock set default 10842,
    alter column origin_region1_short_name set default 'CN',
    alter column origin_region2_id set default 43000000000016,
    alter column freight_template_id set default 'HFT-14851213328261424009',
    alter column shipment_limit_second set default 777600;

with profile_cfg as (
    select
        p.id as profile_id,
        p.is_default,
        max(case when i.config_key = 'temu.shopRefId' then nullif(btrim(i.config_value), '') end) as shop_ref_id,
        max(case when i.config_key = 'default.siteId' then nullif(btrim(i.config_value), '') end) as site_id,
        max(case when i.config_key = 'default.warehouseId' then nullif(btrim(i.config_value), '') end) as warehouse_id,
        max(case when i.config_key = 'sku.defaultStock' then nullif(btrim(i.config_value), '') end) as sku_default_stock,
        max(case when i.config_key = 'sku.maxStock' then nullif(btrim(i.config_value), '') end) as sku_max_stock,
        max(case when i.config_key = 'origin.region1ShortName' then nullif(btrim(i.config_value), '') end) as origin_region1_short_name,
        max(case when i.config_key = 'origin.region2Id' then nullif(btrim(i.config_value), '') end) as origin_region2_id,
        max(case when i.config_key = 'shipment.freightTemplateId' then nullif(btrim(i.config_value), '') end) as freight_template_id,
        max(case when i.config_key = 'shipment.limitSecond' then nullif(btrim(i.config_value), '') end) as shipment_limit_second
    from platform_config_profiles p
    left join platform_config_items i on i.profile_id = p.id
    group by p.id, p.is_default
),
default_cfg as (
    select *
    from profile_cfg
    where is_default = true
    order by profile_id
    limit 1
)
update temu_shops s
set
    site_id = coalesce(s.site_id, cast(pc.site_id as integer), cast(dc.site_id as integer), 100),
    warehouse_id = coalesce(nullif(btrim(s.warehouse_id), ''), pc.warehouse_id, dc.warehouse_id, 'WH-03304781516934009'),
    sku_default_stock = coalesce(s.sku_default_stock, cast(pc.sku_default_stock as integer), cast(dc.sku_default_stock as integer), 1000),
    sku_max_stock = coalesce(s.sku_max_stock, cast(pc.sku_max_stock as integer), cast(dc.sku_max_stock as integer), 10842),
    origin_region1_short_name = coalesce(nullif(btrim(s.origin_region1_short_name), ''), pc.origin_region1_short_name, dc.origin_region1_short_name, 'CN'),
    origin_region2_id = coalesce(s.origin_region2_id, cast(pc.origin_region2_id as bigint), cast(dc.origin_region2_id as bigint), 43000000000016),
    freight_template_id = coalesce(nullif(btrim(s.freight_template_id), ''), pc.freight_template_id, dc.freight_template_id, 'HFT-14851213328261424009'),
    shipment_limit_second = coalesce(s.shipment_limit_second, cast(pc.shipment_limit_second as integer), cast(dc.shipment_limit_second as integer), 777600)
from profile_cfg pc
left join default_cfg dc on true
where pc.shop_ref_id ~ '^[0-9]+$'
  and s.id = cast(pc.shop_ref_id as bigint);

with default_cfg as (
    select
        max(case when i.config_key = 'default.siteId' then nullif(btrim(i.config_value), '') end) as site_id,
        max(case when i.config_key = 'default.warehouseId' then nullif(btrim(i.config_value), '') end) as warehouse_id,
        max(case when i.config_key = 'sku.defaultStock' then nullif(btrim(i.config_value), '') end) as sku_default_stock,
        max(case when i.config_key = 'sku.maxStock' then nullif(btrim(i.config_value), '') end) as sku_max_stock,
        max(case when i.config_key = 'origin.region1ShortName' then nullif(btrim(i.config_value), '') end) as origin_region1_short_name,
        max(case when i.config_key = 'origin.region2Id' then nullif(btrim(i.config_value), '') end) as origin_region2_id,
        max(case when i.config_key = 'shipment.freightTemplateId' then nullif(btrim(i.config_value), '') end) as freight_template_id,
        max(case when i.config_key = 'shipment.limitSecond' then nullif(btrim(i.config_value), '') end) as shipment_limit_second
    from platform_config_profiles p
    left join platform_config_items i on i.profile_id = p.id
    where p.is_default = true
)
update temu_shops s
set
    site_id = coalesce(s.site_id, cast(dc.site_id as integer), 100),
    warehouse_id = coalesce(nullif(btrim(s.warehouse_id), ''), dc.warehouse_id, 'WH-03304781516934009'),
    sku_default_stock = coalesce(s.sku_default_stock, cast(dc.sku_default_stock as integer), 1000),
    sku_max_stock = coalesce(s.sku_max_stock, cast(dc.sku_max_stock as integer), 10842),
    origin_region1_short_name = coalesce(nullif(btrim(s.origin_region1_short_name), ''), dc.origin_region1_short_name, 'CN'),
    origin_region2_id = coalesce(s.origin_region2_id, cast(dc.origin_region2_id as bigint), 43000000000016),
    freight_template_id = coalesce(nullif(btrim(s.freight_template_id), ''), dc.freight_template_id, 'HFT-14851213328261424009'),
    shipment_limit_second = coalesce(s.shipment_limit_second, cast(dc.shipment_limit_second as integer), 777600)
from default_cfg dc;

update temu_shops
set
    site_id = coalesce(site_id, 100),
    warehouse_id = coalesce(nullif(btrim(warehouse_id), ''), 'WH-03304781516934009'),
    sku_default_stock = coalesce(sku_default_stock, 1000),
    sku_max_stock = coalesce(sku_max_stock, 10842),
    origin_region1_short_name = coalesce(nullif(btrim(origin_region1_short_name), ''), 'CN'),
    origin_region2_id = coalesce(origin_region2_id, 43000000000016),
    freight_template_id = coalesce(nullif(btrim(freight_template_id), ''), 'HFT-14851213328261424009'),
    shipment_limit_second = coalesce(shipment_limit_second, 777600);

alter table if exists temu_shops
    alter column site_id set not null,
    alter column warehouse_id set not null,
    alter column sku_default_stock set not null,
    alter column sku_max_stock set not null,
    alter column origin_region1_short_name set not null,
    alter column origin_region2_id set not null,
    alter column freight_template_id set not null,
    alter column shipment_limit_second set not null;
