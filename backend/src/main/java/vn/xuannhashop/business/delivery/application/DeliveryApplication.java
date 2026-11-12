package vn.xuannhashop.business.delivery.application;
import java.util.*;
public interface DeliveryApplication {
    UUID handover(UUID order,List<UUID> items,String accepted,String note,String key);
    UUID returned(UUID order,List<UUID> items,String accepted,String condition,String note,String key);
    List<Map<String,Object>> history(UUID order);List<Map<String,Object>> evidence(UUID order);
    UUID upload(UUID order,byte[] bytes,String filename);AttachmentRepository.Content download(UUID id);
}
