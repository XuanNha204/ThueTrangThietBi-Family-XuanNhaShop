create table sepay_bank_configuration (
 id integer primary key check(id=1), version bigint not null default 0,
 gateway varchar(40), account_number varchar(30), account_holder varchar(120),
 enabled boolean not null default false, automatic_posting boolean not null default false,
 updated_by varchar(80), updated_at timestamptz not null default now(),
 check(not enabled or (gateway is not null and account_number is not null and account_holder is not null))
);
insert into sepay_bank_configuration(id) values(1);
create table bank_payment_request (
 id uuid primary key, order_id uuid not null references rental_order(id),
 code varchar(30) not null unique, kind varchar(30) not null check(kind in ('DEPOSIT_IN','RENTAL_IN','CHARGE_IN')),
 amount numeric(16,0) not null check(amount>0),
 gateway varchar(40) not null, account_number varchar(30) not null, account_holder varchar(120) not null,
 status varchar(20) not null default 'OPEN' check(status in ('OPEN','PAID','CANCELLED','EXPIRED')),
 created_by varchar(80) not null, created_at timestamptz not null, expires_at timestamptz not null,
 money_id uuid unique references money_transaction(id), check(expires_at>created_at)
);
create unique index bank_request_open_once on bank_payment_request(order_id,kind) where status='OPEN';
create index bank_request_order on bank_payment_request(order_id,created_at desc);
alter table sepay_webhook_event
 add column reconciliation_status varchar(20) not null default 'PENDING'
   check(reconciliation_status in ('PENDING','REVIEW','POSTED','IGNORED','TEST')),
 add column reconciliation_reason varchar(100) not null default 'PENDING',
 add column payment_request_id uuid references bank_payment_request(id),
 add column money_id uuid unique references money_transaction(id),
 add column reconciled_by varchar(80), add column reconciled_at timestamptz,
 add column resolution_note varchar(500);
update sepay_webhook_event set reconciliation_status='TEST',reconciliation_reason='SAMPLE' where transaction_id=0;
create index sepay_pending_reconciliation on sepay_webhook_event(received_at) where reconciliation_status='PENDING';
