package vn.xuannhashop.business.rental.application;

import java.time.Instant;
import java.util.*;
import vn.xuannhashop.business.rental.domain.RentalChangeDraft;

public interface RentalChangeApplication {
    record Swap(UUID itemId, UUID equipmentId) {}
    record Request(String kind, long expectedVersion, Instant startsAt, Instant endsAt,
            String address, List<Swap> swaps, String reason, boolean acceptTerms) {}
    RentalChangeDraft quote(UUID order, Request request);
    UUID propose(UUID order, Request request, String key);
    Map<String,Object> history(UUID order);
    void accept(UUID order, UUID change, String key);
    void recordConsent(UUID order, UUID change, String customerName, String basis, String key);
    void decide(UUID order, UUID change, boolean approved, String note, String key);
    void withdraw(UUID order, UUID change, String key);
}
