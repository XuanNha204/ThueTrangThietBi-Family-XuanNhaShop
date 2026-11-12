package vn.xuannhashop.business.pricing.application;

import java.math.BigDecimal;
import java.util.*;
public interface ProductPriceRepository {
    record Price(long version, BigDecimal dailyRate, BigDecimal deposit) {}
    Price get(UUID type, boolean lock);
    void lockForRental(List<UUID> types);
    void change(UUID change, UUID type, Price old, BigDecimal daily, BigDecimal deposit, String reason, String actor);
    List<Map<String,Object>> history(UUID type);
}
