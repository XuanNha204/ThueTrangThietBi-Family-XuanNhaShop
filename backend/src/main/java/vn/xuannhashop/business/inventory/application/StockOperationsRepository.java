package vn.xuannhashop.business.inventory.application;
import java.util.*;
import vn.xuannhashop.business.inventory.application.StockOperationsApplication.*;
public interface StockOperationsRepository {
    Map<String,Object> overview();
    void warehouse(UUID id,String name,String address);
    void lockWarehouses(List<UUID> ids);
    Map<String,Object> asset(UUID id);
    boolean activeType(UUID id);
    void receive(UUID id,Receipt input,String actor);
    Map<String,Object> receipt(UUID id);
    void decideReceipt(UUID id,boolean accepted,String actor,String note);
    void action(UUID id,Action input,Map<String,Object> asset,String actor);
    Map<String,Object> action(UUID id);
    void decideAction(UUID id,boolean accepted,String actor,String note);
    List<Map<String,Object>> countAssets(UUID warehouse);
    void count(UUID id,Count input,List<Map<String,Object>> assets,String actor);
    Map<String,Object> count(UUID id);
    List<Map<String,Object>> countLines(UUID id);
    void decideCount(UUID id,boolean accepted,String actor,String note);
}
