package vn.xuannhashop.business.shared.audit;
import java.util.*;
public interface ActivityRepository {
    void append(String actor,String action,UUID target,String detail);
    List<Map<String,Object>> recent();
    void notifyOrder(UUID order,String event,String payload);
    boolean notifyOnce(UUID order,String event,String payload,String key,String scope);
}
