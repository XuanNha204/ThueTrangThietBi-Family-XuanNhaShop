alter table user_account alter column password_hash drop not null;

create table google_customer_identity (
    subject varchar(255) primary key,
    account_id uuid not null unique references user_account(id) on delete cascade,
    created_at timestamptz not null default now()
);
