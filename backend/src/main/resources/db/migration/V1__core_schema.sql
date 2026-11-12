-- PostgreSQL is the source of truth. No in-memory booking locks.
create table customer (
 id uuid primary key, name varchar(120) not null, phone varchar(20) not null,
 email varchar(160), address varchar(400) not null default '', created_at timestamptz not null default now()
);
create table user_account (
 id uuid primary key, username varchar(80) not null unique, password_hash varchar(100) not null,
 display_name varchar(120) not null, role varchar(30) not null,
 customer_id uuid references customer(id), enabled boolean not null default true,
 check(role in ('OWNER','MANAGER','ACCOUNTANT','INVENTORY','DELIVERY','TECHNICIAN','HR','CUSTOMER'))
);
create table warehouse (id uuid primary key, name varchar(120) not null, address varchar(400) not null);
create table equipment_type (
 id uuid primary key, code varchar(30) not null unique, name varchar(160) not null, category varchar(80) not null,
 description text not null, specifications text not null, icon varchar(30) not null,
 daily_rate numeric(16,2) not null check(daily_rate>0), deposit numeric(16,2) not null check(deposit>=0),
 policy_version varchar(40) not null default 'DEV-1', active boolean not null default true
);
create table equipment (
 id uuid primary key, code varchar(40) not null unique, type_id uuid not null references equipment_type(id),
 warehouse_id uuid not null references warehouse(id), state varchar(40) not null,
 version bigint not null default 0,
 check(state in ('AWAIT_ACCEPTANCE','READY','PREPARING','RENTED','AWAIT_INSPECTION','MAINTENANCE','SUSPENDED','DISPOSED'))
);
create sequence rental_code_seq;
create table rental_order (
 id uuid primary key, code varchar(40) not null unique, customer_id uuid not null references customer(id),
 status varchar(40) not null, starts_at timestamptz not null, ends_at timestamptz not null,
 expires_at timestamptz not null, rental_total numeric(16,2) not null check(rental_total>=0),
 deposit_required numeric(16,2) not null check(deposit_required>=0), address varchar(400) not null,
 terms text not null, agreement_version varchar(40) not null, created_by varchar(80) not null,
 created_at timestamptz not null default now(), version bigint not null default 0,
 check(ends_at>starts_at), check(status in ('HELD','CONFIRMED','PREPARING','RENTED','PARTIAL_RETURN','AWAIT_SETTLEMENT','COMPLETED','CANCELLED','EXPIRED'))
);
create index rental_customer_idx on rental_order(customer_id,created_at desc);
create table rental_item (
 id uuid primary key, order_id uuid not null references rental_order(id), equipment_id uuid not null references equipment(id),
 type_name varchar(160) not null, daily_rate numeric(16,2) not null, rental_amount numeric(16,2) not null,
 deposit_amount numeric(16,2) not null, policy_version varchar(40) not null,
 handed_at timestamptz, returned_at timestamptz, return_condition varchar(30), unique(order_id,equipment_id)
);
create table agreement_revision (
 id uuid primary key, order_id uuid not null references rental_order(id), revision integer not null,
 terms text not null, accepted_by varchar(80) not null, accepted_at timestamptz not null default now(),
 unique(order_id,revision)
);
create table reservation (
 id uuid primary key, order_id uuid not null references rental_order(id), item_id uuid not null references rental_item(id),
 equipment_id uuid not null references equipment(id), occupied_from timestamptz not null, occupied_until timestamptz not null,
 status varchar(20) not null check(status in ('HELD','ALLOCATED','RELEASED')), expires_at timestamptz not null,
 check(occupied_until>occupied_from)
);
create index reservation_equipment_idx on reservation(equipment_id,occupied_from,occupied_until) where status<>'RELEASED';
create table handover (
 id uuid primary key, order_id uuid not null references rental_order(id), kind varchar(20) not null,
 performed_by varchar(80) not null, accepted_by varchar(120) not null, note text not null,
 created_at timestamptz not null default now()
);
create table handover_line (
 id uuid primary key, handover_id uuid not null references handover(id), item_id uuid not null references rental_item(id),
 condition varchar(30) not null, note text not null
);
create table evidence (
 id uuid primary key, order_id uuid not null references rental_order(id), original_name varchar(180) not null,
 file_key varchar(80) not null unique, content_type varchar(40) not null, size bigint not null,
 checksum varchar(64) not null, uploaded_by varchar(80) not null, created_at timestamptz not null default now()
);
create table inspection (
 id uuid primary key, equipment_id uuid not null references equipment(id), checklist_version varchar(40) not null,
 passed boolean not null, findings text not null, inspected_by varchar(80) not null, created_at timestamptz not null default now()
);
create table maintenance_ticket (
 id uuid primary key, equipment_id uuid not null references equipment(id), fault text not null,
 status varchar(20) not null, opened_by varchar(80) not null, opened_at timestamptz not null default now(), closed_at timestamptz
);
create table approval (
 id uuid primary key, type varchar(30) not null, target_id uuid not null, amount numeric(16,2) not null check(amount>=0),
 requester varchar(80) not null, approver varchar(80), reason text not null, status varchar(20) not null default 'PENDING',
 created_at timestamptz not null default now(), decided_at timestamptz,
 check(status in ('PENDING','APPROVED','REJECTED')), check(approver is null or approver<>requester)
);
create table money_transaction (
 id uuid primary key, order_id uuid references rental_order(id), payroll_id uuid,
 kind varchar(30) not null, amount numeric(16,2) not null check(amount>0),
 operation_key varchar(120) not null unique, recorded_by varchar(80) not null,
 note text not null, approval_id uuid references approval(id), created_at timestamptz not null default now(),
 check(kind in ('DEPOSIT_IN','RENTAL_IN','DEPOSIT_REFUND','PAYROLL_OUT','EXPENSE_OUT'))
);
create index money_order_idx on money_transaction(order_id);
create table staff (
 id uuid primary key, name varchar(120) not null, position varchar(100) not null, active boolean not null default true,
 hourly_rate numeric(16,2) not null check(hourly_rate>=0)
);
create table work_record (
 id uuid primary key, staff_id uuid not null references staff(id), work_date date not null,
 hours numeric(5,2) not null check(hours>0 and hours<=24), recorded_by varchar(80) not null,
 approved_by varchar(80), unique(staff_id,work_date), check(approved_by is null or approved_by<>recorded_by)
);
create table salary_policy_version (id varchar(40) primary key, name varchar(120) not null, description text not null);
create table payroll (
 id uuid primary key, staff_id uuid not null references staff(id), period varchar(7) not null,
 approved_hours numeric(8,2) not null, hourly_rate numeric(16,2) not null, amount numeric(16,2) not null,
 policy_version varchar(40) not null references salary_policy_version(id),
 status varchar(20) not null, prepared_by varchar(80) not null, approved_by varchar(80),
 created_at timestamptz not null default now(), unique(staff_id,period),
 check(status in ('DRAFT','APPROVED','PAID')), check(approved_by is null or approved_by<>prepared_by)
);
alter table money_transaction add constraint money_payroll_fk foreign key(payroll_id) references payroll(id);
create unique index payroll_payment_once on money_transaction(payroll_id) where kind='PAYROLL_OUT';
create table payroll_line (id uuid primary key, payroll_id uuid not null references payroll(id), kind varchar(30) not null, amount numeric(16,2) not null, basis text not null);
create table support_ticket (
 id uuid primary key, order_id uuid references rental_order(id), subject varchar(180) not null, message text not null,
 status varchar(20) not null default 'OPEN', created_by varchar(80) not null, resolved_by varchar(80),
 resolution text, created_at timestamptz not null default now(), check(status in ('OPEN','RESOLVED'))
);
create table notification (
 id uuid primary key, order_id uuid references rental_order(id), event_type varchar(60) not null,
 payload text not null, status varchar(20) not null default 'PENDING', attempts integer not null default 0,
 lease_until timestamptz, last_error varchar(200), created_at timestamptz not null default now(), sent_at timestamptz,
 check(status in ('PENDING','PROCESSING','SENT','FAILED'))
);
create index notification_claim_idx on notification(status,lease_until,created_at);
create table audit_entry (
 id uuid primary key, actor varchar(80) not null, action varchar(80) not null, target_id uuid,
 detail text not null, created_at timestamptz not null default now()
);
create table import_batch (
 id uuid primary key, source_key varchar(100) not null unique, filename varchar(180) not null,
 status varchar(20) not null, total_rows integer not null, valid_rows integer not null,
 prepared_by varchar(80) not null, approved_by varchar(80), created_at timestamptz not null default now(),
 check(status in ('STAGED','INVALID','COMMITTED')), check(approved_by is null or approved_by<>prepared_by)
);
create table import_row (
 id uuid primary key, batch_id uuid not null references import_batch(id), row_no integer not null,
 source_id text not null, name text not null, phone text not null,
 address text not null, errors text not null, customer_id uuid references customer(id), unique(batch_id,row_no)
);
create table import_source_link (source_id varchar(60) primary key, customer_id uuid not null references customer(id));
create table report_job (id uuid primary key, requested_by varchar(80) not null, kind varchar(30) not null, scope varchar(120) not null, created_at timestamptz not null default now());
create table command_receipt (
 operation_key varchar(120) primary key, actor varchar(80) not null, fingerprint varchar(64) not null,
 result_id uuid not null, created_at timestamptz not null default now()
);
