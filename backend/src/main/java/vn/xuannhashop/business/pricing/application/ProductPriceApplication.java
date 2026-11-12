package vn.xuannhashop.business.pricing.application;

import java.math.BigDecimal;
import java.util.*;
public interface ProductPriceApplication {
    record Change(Long expectedVersion, BigDecimal dailyRate, BigDecimal deposit, String reason) {}
    UUID change(UUID type, Change command, String key);
    List<Map<String,Object>> history(UUID type);
}
