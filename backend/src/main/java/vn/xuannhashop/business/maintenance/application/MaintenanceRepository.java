package vn.xuannhashop.business.maintenance.application;
import java.util.*;
public interface MaintenanceRepository {
    List<Map<String,Object>> inspections();List<Map<String,Object>> tickets();
    void inspect(UUID id,UUID equipment,boolean passed,String note,String actor);
    void open(UUID id,UUID equipment,String fault,String actor);void close(UUID equipment);
    boolean assessedAfter(UUID equipment,java.time.Instant returnedAt);
}
