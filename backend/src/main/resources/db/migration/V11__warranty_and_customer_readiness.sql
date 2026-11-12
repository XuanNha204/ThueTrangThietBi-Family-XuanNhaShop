create table warranty_coverage (
 equipment_id uuid primary key references equipment(id), version bigint not null default 1,
 provider varchar(160) not null, document_ref varchar(180) not null, terms varchar(4000) not null,
 starts_on date not null, ends_on date not null check(ends_on>=starts_on), updated_by varchar(80) not null, updated_at timestamptz not null default now()
);
create table warranty_case (
 id uuid primary key, equipment_id uuid not null references equipment(id), coverage_version bigint not null,
 provider varchar(160) not null, document_ref varchar(180) not null, terms varchar(4000) not null,
 ends_on date not null, problem varchar(1000) not null, partner_reference varchar(180) not null,
 status varchar(20) not null default 'OPEN' check(status in ('OPEN','CLOSED')),
 opened_by varchar(80) not null, opened_at timestamptz not null default now(), closed_by varchar(80), closed_at timestamptz,
 result varchar(1000), outcome varchar(30) check(outcome in ('REPAIRED','REPLACED','DECLINED','UNRESOLVED'))
);
create unique index warranty_one_open on warranty_case(equipment_id) where status='OPEN';
create table rental_eligibility_policy (
 version bigint primary key, required boolean not null, title varchar(180) not null, criteria varchar(6000) not null,
 reason varchar(1000) not null, published_by varchar(80) not null, published_at timestamptz not null default now()
);
create table customer_verification (
 id uuid primary key, customer_id uuid not null references customer(id), customer_version bigint not null,
 policy_version bigint not null references rental_eligibility_policy(version), passed boolean not null,
 method varchar(180) not null, reference_hint varchar(80) not null, note varchar(1000) not null,
 valid_until date not null, verified_by varchar(80) not null, verified_at timestamptz not null default now()
);
create index customer_verification_latest on customer_verification(customer_id,verified_at desc);
create table equipment_guide (
 id uuid primary key, type_id uuid not null references equipment_type(id), version bigint not null,
 title varchar(180) not null, instructions varchar(12000) not null, safety varchar(6000) not null,
 source varchar(500) not null, published_by varchar(80) not null, published_at timestamptz not null default now(),unique(type_id,version)
);
create table guide_acknowledgement (
 id uuid primary key, order_id uuid not null references rental_order(id), guide_id uuid not null references equipment_guide(id),
 confirmed_by varchar(120) not null, recorded_by varchar(80) not null, method varchar(30) not null,
 evidence varchar(1000) not null, created_at timestamptz not null default now(),unique(order_id,guide_id)
);
create table delivery_assignment (
 id uuid primary key, order_id uuid not null references rental_order(id), kind varchar(10) not null check(kind in ('OUT','IN')),
 assignee varchar(80) not null references user_account(username), starts_at timestamptz not null, ends_at timestamptz not null check(ends_at>starts_at),
 route_note varchar(1000) not null, order_version bigint not null, address_snapshot varchar(400) not null,
 status varchar(20) not null default 'ASSIGNED' check(status in ('ASSIGNED','COMPLETED','CANCELLED')),
 assigned_by varchar(80) not null, created_at timestamptz not null default now(), completed_at timestamptz, outcome varchar(1000)
);
create unique index delivery_one_pending on delivery_assignment(order_id,kind) where status='ASSIGNED';
