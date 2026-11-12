create table inspection_checklist_current (
 type_id uuid primary key references equipment_type(id), version bigint not null default 0 check(version>=0)
);
create table inspection_checklist_revision (
 id uuid primary key, type_id uuid not null references equipment_type(id), version bigint not null check(version>0),
 reason varchar(500) not null, created_by varchar(80) not null, created_at timestamptz not null default now(), unique(type_id,version)
);
create table inspection_checklist_criterion (
 revision_id uuid not null references inspection_checklist_revision(id), code varchar(40) not null,
 label varchar(200) not null, blocking boolean not null, position integer not null,
 primary key(revision_id,code), unique(revision_id,position)
);
alter table inspection add column type_checklist_version bigint not null default 0;
alter table inspection add column results_recorded boolean not null default false;
create table inspection_check_result (
 inspection_id uuid not null references inspection(id), code varchar(40) not null,
 label varchar(200) not null, blocking boolean not null, passed boolean not null, note varchar(500) not null,
 position integer not null, primary key(inspection_id,code), unique(inspection_id,position)
);
