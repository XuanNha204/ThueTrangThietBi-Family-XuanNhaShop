create table authentication_rate_limit (
 bucket varchar(100) primary key,
 window_start timestamptz not null,
 hits integer not null check(hits>0)
);
