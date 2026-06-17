create table if not exists temu_activity_blacklist (
    id bigserial primary key,
    shop_id varchar(64) not null,
    product_id bigint not null,
    goods_id bigint,
    product_name varchar(1000),
    reason varchar(512),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint uk_temu_activity_blacklist_product unique (shop_id, product_id)
);

create index if not exists idx_temu_activity_blacklist_shop
    on temu_activity_blacklist (shop_id);

create index if not exists idx_temu_activity_blacklist_product
    on temu_activity_blacklist (product_id);
