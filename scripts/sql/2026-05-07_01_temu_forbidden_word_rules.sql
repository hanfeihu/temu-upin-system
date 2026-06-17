create table if not exists temu_forbidden_word_rules (
    id bigserial primary key,
    word varchar(256) not null,
    replacement varchar(256),
    field_scope varchar(128),
    enabled boolean not null default true,
    remark text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_temu_forbidden_word_rules_word on temu_forbidden_word_rules(word);
create index if not exists idx_temu_forbidden_word_rules_enabled on temu_forbidden_word_rules(enabled);
create index if not exists idx_temu_forbidden_word_rules_updated_at on temu_forbidden_word_rules(updated_at);

insert into temu_forbidden_word_rules (word, replacement, field_scope, enabled, remark)
select 'Nipple', 'Connector', 'ALL', true, '工业接头语境容易被 TEMU 误判为身体部位/成人敏感词'
where not exists (
    select 1 from temu_forbidden_word_rules where lower(word) = 'nipple'
);
