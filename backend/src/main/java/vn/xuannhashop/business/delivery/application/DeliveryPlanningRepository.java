package vn.xuannhashop.business.delivery.application;
import java.util.*;
import vn.xuannhashop.business.delivery.application.DeliveryPlanningApplication.*;
public interface DeliveryPlanningRepository {
    Map<String,Object> overview(String onlyAssignee);void lockGuide(UUID type);Map<String,Object> guide(UUID type);void publish(UUID id,Guide c,String actor);
    List<Map<String,Object>> orderGuides(UUID order);List<Map<String,Object>> acknowledgements(UUID order);
    Map<String,Object> order(UUID id);void acknowledge(UUID id,UUID order,UUID guide,String confirmed,String evidence,String method,String actor);
    boolean missingAcknowledgement(UUID order,List<UUID> items);
    boolean assignee(String name);boolean overlap(Assignment c);
    void assign(UUID id,Assignment c,String address,String actor);Map<String,Object> assignment(UUID id);
    boolean performed(UUID order,String kind);void finish(UUID id,boolean completed,String outcome);
}
