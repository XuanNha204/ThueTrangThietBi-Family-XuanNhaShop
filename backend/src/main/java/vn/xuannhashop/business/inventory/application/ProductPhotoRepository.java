package vn.xuannhashop.business.inventory.application;

import java.util.UUID;
import vn.xuannhashop.business.inventory.domain.ProductPhoto;

public interface ProductPhotoRepository {
    boolean lockActiveType(UUID typeId);
    UUID currentId(UUID typeId);
    byte[] publicPhoto(UUID typeId, UUID photoId);
    void save(UUID typeId, ProductPhoto photo, byte[] jpeg);
    boolean remove(UUID typeId, UUID photoId);
}
