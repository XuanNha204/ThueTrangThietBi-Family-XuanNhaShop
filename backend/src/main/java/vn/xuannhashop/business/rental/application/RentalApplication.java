package vn.xuannhashop.business.rental.application;
import java.time.Instant;
import java.util.*;
public interface RentalApplication {
    record Line(UUID typeId,int quantity,Long priceVersion) { public Line(UUID typeId,int quantity){this(typeId,quantity,null);} }
    record Create(UUID customerId,List<Line> lines,Instant startsAt,Instant endsAt,String address,boolean acceptTerms) {}
    Map<String,Object> quote(Create command); UUID hold(Create command,String key);
    List<Map<String,Object>> list(); Map<String,Object> detail(UUID id);
    void confirm(UUID id,String key); void prepare(UUID id,String key); void cancel(UUID id,String key);
    void settle(UUID id,String key); void handover(UUID id,List<UUID> items); void returned(UUID id,List<UUID> items,String condition);
    void expireHolds(); void assertAccess(UUID id);
}
