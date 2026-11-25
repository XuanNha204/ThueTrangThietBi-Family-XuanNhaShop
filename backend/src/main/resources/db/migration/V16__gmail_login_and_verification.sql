alter table user_account add column login_gmail varchar(80) unique;
alter table user_account add constraint login_gmail_normalized
    check (login_gmail is null or login_gmail ~ '^[a-z0-9]{1,64}@gmail[.]com$');

create table gmail_login_challenge (
    id uuid primary key,
    digest bytea not null,
    expires_at timestamptz not null,
    attempts_left integer not null default 5 check (attempts_left between 0 and 5),
    consumed boolean not null default false
);
create index gmail_login_challenge_expiry on gmail_login_challenge(expires_at);

create table gmail_mail_limit (
    inbox_digest varchar(64) primary key,
    window_started timestamptz not null,
    last_sent timestamptz not null,
    sends integer not null check (sends between 1 and 5)
);
create index gmail_mail_limit_last_sent on gmail_mail_limit(last_sent);
