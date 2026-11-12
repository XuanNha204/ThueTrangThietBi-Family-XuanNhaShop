-- Receipt only: no trigger or automatic posting to the money ledger.
create table sepay_webhook_event (
 id uuid primary key,
 channel varchar(40) not null,
 receipt_key varchar(150) not null unique,
 transaction_id bigint not null check(transaction_id>=0),
 gateway varchar(100) not null,
 account_number varchar(100) not null,
 occurred_at timestamptz not null,
 transfer_type varchar(3) not null check(transfer_type in ('in','out')),
 amount numeric(18,0) not null check(amount>0),
 payment_code varchar(200), reference_code varchar(200),
 fingerprint char(64) not null, body_sha256 char(64) not null,
 raw_body text not null,
 status varchar(20) not null check(status in ('TEST','RECEIVED','CONFLICT')),
 delivery_count integer not null default 1,
 conflict_count integer not null default 0,
 received_at timestamptz not null default clock_timestamp(),
 last_received_at timestamptz not null default clock_timestamp()
);
create unique index sepay_transaction_once on sepay_webhook_event(channel,transaction_id) where transaction_id>0;
create table sepay_webhook_conflict (
 id uuid primary key,event_id uuid not null references sepay_webhook_event(id),
 fingerprint char(64) not null,raw_body text not null,
 received_at timestamptz not null default clock_timestamp(),
 unique(event_id,fingerprint)
);
