package vn.xuannhashop.business.inventory.application;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.inventory.domain.ProductPhoto;
import vn.xuannhashop.business.shared.Checks;
import vn.xuannhashop.business.shared.Commands;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class ProductPhotoService implements ProductPhotoApplication {
    private final ProductPhotoRepository repository;
    private final ProductImageEncoder encoder;
    private final ActorContext actor;
    private final Commands commands;
    private final ActivityService audit;

    public ProductPhotoService(ProductPhotoRepository repository, ProductImageEncoder encoder,
                               ActorContext actor, Commands commands, ActivityService audit) {
        this.repository = repository;
        this.encoder = encoder;
        this.actor = actor;
        this.commands = commands;
        this.audit = audit;
    }

    @Override
    public byte[] publicPhoto(UUID typeId, UUID photoId) {
        byte[] image = repository.publicPhoto(typeId, photoId);
        if (image == null) throw DomainException.missing();
        return image;
    }

    @Override
    @Transactional
    public UUID save(UUID typeId, Upload upload, String key) {
        actor.require("OWNER");
        if (upload.image() == null || upload.image().length == 0 || upload.image().length > 5 * 1024 * 1024) {
            throw DomainException.bad("Chọn ảnh JPEG hoặc PNG tối đa 5 MB.");
        }
        String credit = upload.credit() == null || upload.credit().isBlank()
            ? null : Checks.text(upload.credit(), 200, "Ghi công");
        String source = httpsUrl(upload.sourceUrl(), "Liên kết nguồn");
        String license = httpsUrl(upload.licenseUrl(), "Liên kết giấy phép");
        UUID photoId = UUID.randomUUID();
        var photo = new ProductPhoto(photoId, upload.illustrative(), credit, source, license);
        byte[] jpeg = encoder.toJpeg(upload.image());
        // Length-prefix each free-text field so different metadata cannot share a command fingerprint.
        String fingerprint = "PRODUCT_PHOTO:" + typeId + ":" + digest(upload.image()) + ":" + upload.illustrative()
            + ":" + upload.expectedPhotoId() + ":" + field(credit) + field(source) + field(license);
        return commands.once(key, fingerprint, () -> {
            if (!repository.lockActiveType(typeId)) throw DomainException.missing();
            if (!Objects.equals(upload.expectedPhotoId(), repository.currentId(typeId))) {
                throw DomainException.conflict("Ảnh đã thay đổi. Tải lại danh mục rồi thử lại.");
            }
            repository.save(typeId, photo, jpeg);
            audit.record("PRODUCT_PHOTO_SAVED", typeId, photoId.toString());
            return photoId;
        });
    }

    @Override
    @Transactional
    public void remove(UUID typeId, UUID photoId, String key) {
        actor.require("OWNER");
        commands.once(key, "PRODUCT_PHOTO_REMOVE:" + typeId + ":" + photoId, () -> {
            if (!repository.lockActiveType(typeId)) throw DomainException.missing();
            if (!repository.remove(typeId, photoId)) {
                throw DomainException.conflict("Ảnh đã thay đổi. Tải lại danh mục rồi thử lại.");
            }
            audit.record("PRODUCT_PHOTO_REMOVED", typeId, photoId.toString());
            return photoId;
        });
    }

    private String httpsUrl(String raw, String label) {
        if (raw == null || raw.isBlank()) return null;
        String value = Checks.text(raw, 1500, label);
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Return the same validation error for malformed and unsupported links.
        }
        throw DomainException.bad(label + " phải là địa chỉ HTTPS hợp lệ.");
    }

    private String digest(byte[] image) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String field(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }
}
