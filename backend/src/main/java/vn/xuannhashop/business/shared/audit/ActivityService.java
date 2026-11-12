package vn.xuannhashop.business.shared.audit;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.xuannhashop.business.shared.security.ActorContext;
@Service
public class ActivityService {
    private final ActivityRepository repository; private final ActorContext actor;
    public ActivityService(ActivityRepository repository,ActorContext actor) { this.repository=repository;this.actor=actor; }
    public void record(String action,UUID target,String detail) { repository.append(actor.username(),action,target,detail); }
    public void system(String action,UUID target,String detail) { repository.append("SYSTEM",action,target,detail); }
    public void notifyOrder(UUID order,String event,String payload) { repository.notifyOrder(order,event,payload); }
    public boolean notifyOnce(UUID order,String event,String payload,String key,String scope){return repository.notifyOnce(order,event,payload,key,scope);}
    public List<Map<String,Object>> recent() { actor.require("OWNER");return repository.recent(); }
}
