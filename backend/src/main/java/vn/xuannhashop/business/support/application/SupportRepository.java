package vn.xuannhashop.business.support.application;
import java.util.*;
public interface SupportRepository {
    List<Map<String,Object>> tickets(String username);void create(UUID id,UUID order,String subject,String message,String actor);
    void resolve(UUID id,String resolution,String actor);Map<String,Object> ticket(UUID id);
    List<Map<String,Object>> notifications();List<Map<String,Object>> claim();void sent(UUID id,int attempt);void failed(UUID id,int attempt);
}
