create table supplier (
 id uuid primary key, name varchar(160) not null, contact varchar(300) not null, document_ref varchar(180) not null,
 created_by varchar(80) not null, created_at timestamptz not null default now()
);
create table expense (
 id uuid primary key, supplier_id uuid references supplier(id), purchase_receipt_id uuid unique references purchase_receipt(id),
 equipment_id uuid references equipment(id), category varchar(30) not null check(category in ('PURCHASE','MAINTENANCE','CLEANING','TRANSPORT','OTHER')),
 incurred_on date not null, due_on date not null, amount numeric(16,2) not null check(amount>0),
 document_ref varchar(180) not null, description varchar(1000) not null,
 status varchar(20) not null default 'PENDING' check(status in ('PENDING','APPROVED','REJECTED')),
 requested_by varchar(80) not null, decided_by varchar(80), decision_note varchar(1000),
 created_at timestamptz not null default now(), decided_at timestamptz, check(decided_by is null or decided_by<>requested_by)
);
alter table money_transaction add column expense_id uuid references expense(id);
create table shift_reconciliation (
 id uuid primary key, starts_at timestamptz not null, ends_at timestamptz not null check(ends_at>starts_at),
 opening_balance numeric(16,2) not null, counted_balance numeric(16,2) not null, expected_balance numeric(16,2) not null,
 difference numeric(16,2) not null, ledger_fingerprint text not null, reason varchar(1000) not null,
 status varchar(20) not null default 'PENDING' check(status in ('PENDING','APPROVED','REJECTED')),
 requested_by varchar(80) not null, decided_by varchar(80), decision_note varchar(1000),
 created_at timestamptz not null default now(), decided_at timestamptz, check(decided_by is null or decided_by<>requested_by)
);
create index expense_supplier_idx on expense(supplier_id,status,due_on);
create index money_expense_idx on money_transaction(expense_id);
