package vn.xuannhashop.presentation.api;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.xuannhashop.business.inventory.application.ProductPhotoApplication;
import vn.xuannhashop.business.shared.DomainException;

@RestController
public class ProductPhotoController {
    private final ProductPhotoApplication photos;

    public ProductPhotoController(ProductPhotoApplication photos) {
        this.photos = photos;
    }

    @GetMapping("/api/catalog/{typeId}/photo/{photoId}")
    ResponseEntity<byte[]> get(@PathVariable UUID typeId, @PathVariable UUID photoId) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(photos.publicPhoto(typeId, photoId));
    }

    @PostMapping(value = "/api/equipment-types/{typeId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, UUID> save(@PathVariable UUID typeId, @RequestParam(required = false) MultipartFile file,
                         @RequestParam(required = false) UUID expectedPhotoId,
                         @RequestParam(defaultValue = "false") boolean illustrative,
                         @RequestParam(required = false) String credit,
                         @RequestParam(required = false) String sourceUrl,
                         @RequestParam(required = false) String licenseUrl,
                         @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException e) {
            throw DomainException.bad("Không đọc được tệp tải lên. Hãy chọn ảnh lại.");
        }
        var upload = new ProductPhotoApplication.Upload(data, expectedPhotoId, illustrative, credit, sourceUrl, licenseUrl);
        return Map.of("id", photos.save(typeId, upload, key));
    }

    @DeleteMapping("/api/equipment-types/{typeId}/photo/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID typeId, @PathVariable UUID photoId,
                @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        photos.remove(typeId, photoId, key);
    }
}
