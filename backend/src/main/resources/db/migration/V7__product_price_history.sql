alter table equipment_type add column price_version bigint not null default 0 check(price_version>=0);
create table product_price_change (
 id uuid primary key, type_id uuid not null references equipment_type(id),
 version bigint not null, old_daily_rate numeric(16,2) not null, daily_rate numeric(16,2) not null,
 old_deposit numeric(16,2) not null, deposit numeric(16,2) not null,
 reason varchar(500) not null, changed_by varchar(60) not null, changed_at timestamptz not null default now(),
 unique(type_id,version), check(daily_rate>0), check(deposit>=0)
);
