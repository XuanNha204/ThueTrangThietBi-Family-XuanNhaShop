package vn.xuannhashop.business.maintenance.application;
import java.util.*;
import vn.xuannhashop.business.maintenance.application.WarrantyApplication.Coverage;
public interface WarrantyRepository {
    Map<String,Object> overview();Map<String,Object> coverage(UUID equipment);
    void coverage(Coverage c,String actor);void open(UUID id,UUID equipment,Map<String,Object> coverage,String problem,String reference,String actor);
    Map<String,Object> claim(UUID id);void close(UUID id,String outcome,String result,String actor);
}
