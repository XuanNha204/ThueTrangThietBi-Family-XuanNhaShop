package vn.xuannhashop.business.maintenance.application;
import java.time.Instant;
import java.util.*;
public interface MaintenancePlanningRepository {
    List<Map<String,Object>> plans();
    Map<String,Object> lock(UUID plan);
    void schedule(UUID id,UUID equipment,Instant from,Instant to,String reason,String actor);
    void start(UUID id,String actor);
    void finish(UUID id,String status,String note,String actor,UUID inspection);
}
