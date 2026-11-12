package vn.xuannhashop.business.delivery.application;
import java.util.*;
public interface DeliveryRepository {
    UUID record(UUID order,List<UUID> items,String kind,String accepted,String condition,String note,String actor);
    List<Map<String,Object>> list(UUID order);
    void evidence(UUID id,UUID order,AttachmentRepository.Stored file,String actor);
    Map<String,Object> evidence(UUID id); List<Map<String,Object>> evidenceFor(UUID order);
}
