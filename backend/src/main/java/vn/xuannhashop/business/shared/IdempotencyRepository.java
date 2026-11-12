package vn.xuannhashop.business.shared;
import java.util.UUID;
public interface IdempotencyRepository {
    void lock(String key);
    Receipt find(String key);
    void save(String key,String actor,String fingerprint,UUID result);
    record Receipt(String actor,String fingerprint,UUID resultId) {}
}
