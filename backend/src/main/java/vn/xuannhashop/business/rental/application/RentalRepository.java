package vn.xuannhashop.business.rental.application;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import vn.xuannhashop.business.rental.domain.RentalOrder;
public interface RentalRepository {
    String nextCode(); void create(RentalOrder o,String actor);
    void item(UUID id,UUID order,UUID equipment,String name,BigDecimal daily,BigDecimal rental,BigDecimal deposit,String policy);
    RentalOrder get(UUID id,boolean lock); List<Map<String,Object>> list(UUID customer);
    List<Map<String,Object>> items(UUID order); void status(UUID order,String status);
    void handover(UUID item,Instant at); void returned(UUID item,Instant at,String condition);
    List<UUID> expired();
    void captureInitialAgreement(UUID order);
    void resolveLost(UUID item);
    List<Map<String,Object>> attention();
    List<Map<String,Object>> due(int hours);
    List<Map<String,Object>> notices(UUID order,boolean customer);
    void closePendingChanges(UUID order,String reason);
}
