package vn.xuannhashop.business.maintenance.application;
import java.time.Instant;
import java.util.*;
public interface MaintenancePlanningApplication {
    List<Map<String,Object>> plans();
    UUID schedule(UUID equipment,Instant from,Instant to,String reason,String key);
    void start(UUID plan,String key);
    void cancel(UUID plan,String reason,String key);
    void complete(UUID plan,boolean powerSafe,boolean clean,boolean complete,String findings,String key);
    void complete(UUID plan,InspectionChecklistApplication.Input input,String key);
}
