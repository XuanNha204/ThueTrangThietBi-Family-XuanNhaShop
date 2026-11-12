package vn.xuannhashop.business.rental.application;
import java.math.BigDecimal;
import java.util.*;
public interface RentalIncidentApplication {
    record Report(UUID itemId,String kind,BigDecimal amount,String description,String customerName,String agreementBasis) {}
    List<Map<String,Object>> list(UUID order);
    UUID report(UUID order,Report report,String key);
    void decide(UUID order,UUID incident,boolean approved,String note,String key);
}
