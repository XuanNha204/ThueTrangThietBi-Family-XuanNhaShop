-- Rename the previously seeded warehouse on existing development databases.
update warehouse
set name = 'Kho XuanNha'
where name = 'Kho Xuân Nhà';
