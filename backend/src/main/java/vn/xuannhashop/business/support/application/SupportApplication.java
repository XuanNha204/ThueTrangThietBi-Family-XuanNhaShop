package vn.xuannhashop.business.support.application;
import java.util.*;
public interface SupportApplication {
    List<Map<String,Object>> tickets();UUID create(UUID order,String subject,String message,String key);
    void resolve(UUID id,String resolution,String key);List<Map<String,Object>> notifications();void deliverNotifications();
}
