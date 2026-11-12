package vn.xuannhashop.business.rental.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable quotation. Acceptance never silently recomputes a customer's price. */
public record RentalChangeDraft(UUID orderId, String kind, long baseVersion,
        Instant startsAt, Instant endsAt, String address, BigDecimal previousTotal,
        BigDecimal rentalTotal, BigDecimal depositRequired, String reason, List<Item> items) {
    public record Item(UUID itemId, UUID equipmentId, UUID typeId, String equipmentCode,
            String name, BigDecimal dailyRate, BigDecimal rentalAmount,
            BigDecimal depositAmount, String policyVersion, boolean outstanding) {}
}
