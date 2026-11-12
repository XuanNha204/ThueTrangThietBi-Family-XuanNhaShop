package vn.xuannhashop.business.maintenance.application;
import java.util.*;
public interface MaintenanceApplication {
    List<Map<String,Object>> inspections();List<Map<String,Object>> tickets();
    UUID inspect(UUID equipment,boolean powerSafe,boolean clean,boolean complete,String findings,String key);
    UUID inspect(UUID equipment,InspectionChecklistApplication.Input input,String key);
    UUID open(UUID equipment,String fault,String key);
    boolean assessedAfter(UUID equipment,java.time.Instant returnedAt);
}
