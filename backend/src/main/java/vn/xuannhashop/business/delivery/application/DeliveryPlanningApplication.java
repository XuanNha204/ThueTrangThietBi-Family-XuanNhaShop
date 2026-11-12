package vn.xuannhashop.business.delivery.application;
import java.util.*;import java.time.Instant;
public interface DeliveryPlanningApplication {
    record Guide(UUID typeId,Long expectedVersion,String title,String instructions,String safety,String source){}
    record Assignment(UUID orderId,String kind,String assignee,Instant startsAt,Instant endsAt,String routeNote,Long orderVersion){}
    Map<String,Object> overview();Map<String,Object> guide(UUID type);
    UUID publish(Guide input,String key);Map<String,Object> orderGuides(UUID order);
    UUID acknowledge(UUID order,UUID guide,String confirmedBy,String evidence,String key);
    void requireAcknowledged(UUID order,List<UUID> items);
    UUID assign(Assignment input,String key);void finish(UUID id,boolean completed,String outcome,String key);
}
