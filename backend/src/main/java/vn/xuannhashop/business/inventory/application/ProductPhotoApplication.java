package vn.xuannhashop.business.inventory.application;

import java.util.UUID;

public interface ProductPhotoApplication {
    record Upload(byte[] image, UUID expectedPhotoId, boolean illustrative, String credit, String sourceUrl, String licenseUrl) {}

    byte[] publicPhoto(UUID typeId, UUID photoId);
    UUID save(UUID typeId, Upload upload, String key);
    void remove(UUID typeId, UUID photoId, String key);
}
