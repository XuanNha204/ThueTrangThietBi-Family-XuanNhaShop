package vn.xuannhashop.business.maintenance.application;
import java.util.*;
import java.time.LocalDate;
public interface WarrantyApplication {
    record Coverage(UUID equipmentId,Long expectedVersion,String provider,String documentRef,String terms,LocalDate startsOn,LocalDate endsOn) {}
    Map<String,Object> overview();
    UUID coverage(Coverage input,String key);
    UUID open(UUID equipmentId,String problem,String partnerReference,String key);
    void close(UUID id,String outcome,String result,String key);
}
