package vn.xuannhashop.infrastructure.persistence;

import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.inventory.application.ProductPhotoRepository;
import vn.xuannhashop.business.inventory.domain.ProductPhoto;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

@Repository
public class JdbcProductPhotoRepository implements ProductPhotoRepository {
    private final Sql sql;

    public JdbcProductPhotoRepository(Sql sql) {
        this.sql = sql;
    }

    @Override
    public boolean lockActiveType(UUID typeId) {
        return sql.one("select id from equipment_type where id=:type and active for update", p("type", typeId)) != null;
    }

    @Override
    public UUID currentId(UUID typeId) {
        var row = sql.one("select photo_id from equipment_type_photo where equipment_type_id=:type", p("type", typeId));
        return row == null ? null : (UUID) row.get("photo_id");
    }

    @Override
    public byte[] publicPhoto(UUID typeId, UUID photoId) {
        var row = sql.one("""
            select p.jpeg from equipment_type_photo p
            join equipment_type t on t.id = p.equipment_type_id
            where t.id = :type and t.active and p.photo_id = :photo
            """, p("type", typeId, "photo", photoId));
        return row == null ? null : (byte[]) row.get("jpeg");
    }

    @Override
    public void save(UUID typeId, ProductPhoto photo, byte[] jpeg) {
        sql.update("""
            insert into equipment_type_photo (equipment_type_id, photo_id, jpeg, illustrative, credit, source_url, license_url)
            values (:type, :photo, :jpeg, :illustrative, :credit, :source, :license)
            on conflict (equipment_type_id) do update set
                photo_id = excluded.photo_id, jpeg = excluded.jpeg, illustrative = excluded.illustrative,
                credit = excluded.credit, source_url = excluded.source_url, license_url = excluded.license_url
            """, p("type", typeId, "photo", photo.id(), "jpeg", jpeg, "illustrative", photo.illustrative(),
            "credit", photo.credit(), "source", photo.sourceUrl(), "license", photo.licenseUrl()));
    }

    @Override
    public boolean remove(UUID typeId, UUID photoId) {
        return sql.update("delete from equipment_type_photo where equipment_type_id=:type and photo_id=:photo",
            p("type", typeId, "photo", photoId)) == 1;
    }
}
