package vn.xuannhashop.business.rental.application;
import java.util.*;
public interface RentalIncidentRepository {
    List<Map<String,Object>> list(UUID order);
    Map<String,Object> get(UUID id,boolean lock);
    void create(UUID id,UUID order,RentalIncidentApplication.Report report,String actor);
    void decide(UUID id,boolean approved,String actor,String note);
}
