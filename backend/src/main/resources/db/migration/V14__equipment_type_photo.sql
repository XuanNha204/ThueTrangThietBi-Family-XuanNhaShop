create table equipment_type_photo (
    equipment_type_id uuid primary key references equipment_type(id) on delete cascade,
    photo_id uuid unique not null,
    jpeg bytea not null check (octet_length(jpeg) between 1 and 2097152),
    illustrative boolean not null,
    credit varchar(200),
    source_url varchar(1500),
    license_url varchar(1500)
);
