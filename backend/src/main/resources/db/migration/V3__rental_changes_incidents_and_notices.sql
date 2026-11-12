alter table customer add column version bigint not null default 0;
alter table rental_item add column resolved_at timestamptz;
alter table rental_item add column resolution_kind varchar(20);
alter table rental_item add constraint rental_item_resolution_check
 check ((resolved_at is null and resolution_kind is null) or
        (resolved_at is not null and resolution_kind='LOST' and returned_at is null));
alter table reservation add column created_at timestamptz not null default now();

create table rental_change (
 id uuid primary key, order_id uuid not null references rental_order(id),
 kind varchar(20) not null check(kind in ('EXTEND','RESCHEDULE','ADDRESS','SWAP')),
 base_version bigint not null, draft jsonb not null, requested_by varchar(80) not null,
 requester_role varchar(30) not null, customer_accepted_by varchar(120),
 consent_recorded_by varchar(80), consent_note text,
 decided_by varchar(80), decision_note text, decided_at timestamptz,
 status varchar(20) not null default 'PENDING' check(status in ('PENDING','APPLIED','REJECTED','CANCELLED')),
 created_at timestamptz not null default now()
);
create unique index rental_change_one_pending on rental_change(order_id) where status='PENDING';
alter table agreement_revision add column requested_by varchar(80);
alter table agreement_revision add column staff_confirmed_by varchar(80);
alter table agreement_revision add column customer_confirmed_by varchar(120);
alter table agreement_revision add column consent_note text;
alter table agreement_revision add column change_id uuid unique references rental_change(id);
alter table agreement_revision add column snapshot jsonb;
update agreement_revision a set snapshot=jsonb_build_object(
 'startsAt',o.starts_at,'endsAt',o.ends_at,'address',o.address,
 'rentalTotal',o.rental_total,'depositRequired',o.deposit_required,
 'items',(select coalesce(jsonb_agg(jsonb_build_object('itemId',i.id,'equipmentId',i.equipment_id,
 'equipmentCode',e.code,'name',i.type_name,'dailyRate',i.daily_rate,'rentalAmount',i.rental_amount,
 'deposit',i.deposit_amount,'policyVersion',i.policy_version) order by e.code),'[]'::jsonb)
 from rental_item i join equipment e on e.id=i.equipment_id where i.order_id=o.id))
 from rental_order o where o.id=a.order_id;

create table rental_incident (
 id uuid primary key, order_id uuid not null references rental_order(id),
 item_id uuid not null references rental_item(id), kind varchar(20) not null check(kind in ('LOST','DAMAGE')),
 amount numeric(16,2) not null check(amount>=0), description text not null,
 customer_name varchar(120) not null, agreement_basis text not null,
 requested_by varchar(80) not null, decided_by varchar(80), decision_note text,
 status varchar(20) not null default 'PENDING' check(status in ('PENDING','APPROVED','REJECTED')),
 created_at timestamptz not null default now(), decided_at timestamptz,
 check(decided_by is null or decided_by<>requested_by)
);
create unique index rental_incident_one_resolution on rental_incident(item_id,kind) where status in ('PENDING','APPROVED');
create table rental_charge (
 id uuid primary key, order_id uuid not null references rental_order(id),
 incident_id uuid not null unique references rental_incident(id), amount numeric(16,2) not null check(amount>=0),
 reason text not null, approved_by varchar(80) not null, created_at timestamptz not null default now()
);
alter table money_transaction drop constraint money_transaction_kind_check;
alter table money_transaction add constraint money_transaction_kind_check
 check(kind in ('DEPOSIT_IN','RENTAL_IN','DEPOSIT_REFUND','PAYROLL_OUT','EXPENSE_OUT','CHARGE_IN'));
alter table notification add column dedupe_key varchar(180) unique;
alter table notification add column recipient_scope varchar(20) not null default 'CUSTOMER'
 check(recipient_scope in ('CUSTOMER','STAFF'));
create index rental_due_idx on rental_order(ends_at) where status in ('RENTED','PARTIAL_RETURN');
