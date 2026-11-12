-- B03 owns scheduling blocks, B07 owns maintenance work and outcomes.
create table maintenance_window (
 id uuid primary key, equipment_id uuid not null references equipment(id),
 occupied_from timestamptz not null, occupied_until timestamptz not null,
 active boolean not null default true, check(occupied_until>occupied_from)
);
create index maintenance_window_asset on maintenance_window(equipment_id,occupied_from) where active;
create table maintenance_plan (
 id uuid primary key references maintenance_window(id), equipment_id uuid not null references equipment(id),
 starts_at timestamptz not null, ends_at timestamptz not null, reason varchar(1000) not null,
 status varchar(20) not null default 'SCHEDULED' check(status in ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
 created_by varchar(80) not null, created_at timestamptz not null default now(),
 started_by varchar(80), started_at timestamptz, completed_by varchar(80), completed_at timestamptz,
 completion_note varchar(1000), inspection_id uuid references inspection(id), check(ends_at>starts_at)
);
