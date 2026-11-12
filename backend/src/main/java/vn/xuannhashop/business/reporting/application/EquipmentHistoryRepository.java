package vn.xuannhashop.business.reporting.application;
import java.time.Instant;
import java.util.*;
public interface EquipmentHistoryRepository {
    Map<String,Object> equipment(UUID id);
    List<Map<String,Object>> events(UUID id,Instant before,String beforeKey);
}
