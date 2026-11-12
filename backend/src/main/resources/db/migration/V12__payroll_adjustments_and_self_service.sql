alter table staff add column user_id uuid unique references user_account(id);
create table payroll_adjustment (
 id uuid primary key, staff_id uuid not null references staff(id), period varchar(7) not null,
 kind varchar(30) not null check(kind in ('ALLOWANCE','BONUS','DEDUCTION','ADVANCE')),
 amount numeric(16,2) not null check(amount>0), basis varchar(1000) not null,
 status varchar(20) not null default 'PENDING' check(status in ('PENDING','APPROVED','REJECTED')),
 requested_by varchar(80) not null, decided_by varchar(80), decision_note varchar(1000),
 created_at timestamptz not null default now(), decided_at timestamptz,check(decided_by is null or decided_by<>requested_by)
);
alter table payroll_line add column adjustment_id uuid unique references payroll_adjustment(id);
alter table money_transaction drop constraint money_transaction_kind_check;
alter table money_transaction add constraint money_transaction_kind_check check(kind in ('DEPOSIT_IN','RENTAL_IN','CHARGE_IN','DEPOSIT_REFUND','PAYROLL_OUT','EXPENSE_OUT','PAYROLL_ADVANCE_OUT','RECOVERY_IN'));
create table payroll_question (
 id uuid primary key, payroll_id uuid not null references payroll(id), requested_by varchar(80) not null,
 question varchar(1000) not null, status varchar(20) not null default 'OPEN' check(status in ('OPEN','RESOLVED')),
 answer varchar(1000), answered_by varchar(80), created_at timestamptz not null default now(), answered_at timestamptz
);
