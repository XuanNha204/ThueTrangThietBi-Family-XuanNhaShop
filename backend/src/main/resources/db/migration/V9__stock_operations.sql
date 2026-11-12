alter table equipment add column serial_number varchar(120);
alter table equipment add column acquired_on date;
alter table equipment add column purchase_cost numeric(16,2) not null default 0 check(purchase_cost>=0);
alter table equipment add column useful_life_months integer not null default 0 check(useful_life_months between 0 and 600);
alter table equipment add column warranty_until date;
alter table equipment add column supplier_name varchar(160);
alter table equipment add column document_ref varchar(180);
create table purchase_receipt (
 id uuid primary key, warehouse_id uuid not null references warehouse(id), type_id uuid not null references equipment_type(id),
 supplier_name varchar(160) not null, document_ref varchar(180) not null, acquired_on date not null, warranty_until date,
 useful_life_months integer not null check(useful_life_months between 0 and 600), unit_cost numeric(16,2) not null check(unit_cost>0),
 status varchar(20) not null default 'PENDING' check(status in ('PENDING','ACCEPTED','REJECTED')),
 requested_by varchar(80) not null, decided_by varchar(80), reason varchar(1000) not null, decision_note varchar(1000),
 created_at timestamptz not null default now(), decided_at timestamptz, check(decided_by is null or decided_by<>requested_by)
);
create table purchase_receipt_line (
 id uuid primary key, receipt_id uuid not null references purchase_receipt(id), code varchar(40) not null,
 serial_number varchar(120) not null, equipment_id uuid references equipment(id), unique(receipt_id,code)
);
create table stock_action (
 id uuid primary key, kind varchar(20) not null check(kind in ('TRANSFER','DISPOSE')), equipment_id uuid not null references equipment(id),
 from_warehouse uuid not null references warehouse(id), to_warehouse uuid references warehouse(id), previous_state varchar(40) not null,
 recovery_value numeric(16,2) not null default 0 check(recovery_value>=0), reason varchar(1000) not null,
 status varchar(20) not null default 'PENDING' check(status in ('PENDING','COMPLETED','REJECTED')),
 requested_by varchar(80) not null, decided_by varchar(80), decision_note varchar(1000),
 created_at timestamptz not null default now(), decided_at timestamptz, check(decided_by is null or decided_by<>requested_by),
 check((kind='TRANSFER' and to_warehouse is not null and to_warehouse<>from_warehouse) or (kind='DISPOSE' and to_warehouse is null))
);
create unique index stock_action_pending on stock_action(equipment_id) where status='PENDING';
create table stock_count (
 id uuid primary key, warehouse_id uuid not null references warehouse(id), observed_codes text not null,
 reason varchar(1000) not null, status varchar(20) not null default 'PENDING' check(status in ('PENDING','APPROVED','REJECTED')),
 requested_by varchar(80) not null, decided_by varchar(80), decision_note varchar(1000),
 created_at timestamptz not null default now(), decided_at timestamptz, check(decided_by is null or decided_by<>requested_by)
);
create table stock_count_line (
 count_id uuid not null references stock_count(id), equipment_id uuid not null references equipment(id),
 code varchar(40) not null, version bigint not null, expected_state varchar(40) not null, found boolean not null,
 primary key(count_id,equipment_id)
);
