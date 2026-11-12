package vn.xuannhashop.business.maintenance.application;
import java.util.*;
import vn.xuannhashop.business.maintenance.application.InspectionChecklistApplication.*;
public interface InspectionChecklistRepository {
    Template current(UUID type,boolean lock);
    void publish(UUID id,UUID type,long version,List<Criterion> checks,String reason,String actor);
    List<Map<String,Object>> history(UUID type);
    void results(UUID inspection,long version,List<Result> results);
}
