package vn.xuannhashop.business.support.application;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.shared.security.ActorContext;
import vn.xuannhashop.business.rental.application.RentalApplication;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
@Service
public class SupportNotificationService implements SupportApplication {
    private final SupportRepository r;private final RentalApplication rentals;private final ActorContext actor;private final ActivityService audit;private final Commands commands;private final NotificationPort port;
    public SupportNotificationService(SupportRepository r,RentalApplication rentals,ActorContext a,ActivityService au,Commands c,NotificationPort p){this.r=r;this.rentals=rentals;actor=a;audit=au;commands=c;port=p;}
    public List<Map<String,Object>> tickets(){var u=actor.current();if(!u.role().equals("CUSTOMER"))actor.require("OWNER","MANAGER","DELIVERY","INVENTORY");return r.tickets(u.role().equals("CUSTOMER")?u.username():null);}
    @Transactional public UUID create(UUID order,String subject,String message,String key){actor.require("OWNER","MANAGER","DELIVERY","INVENTORY","CUSTOMER");Checks.text(subject,180,"Tiêu đề");Checks.text(message,2000,"Nội dung");return commands.once(key,"SUPPORT:"+order+":"+subject+":"+message,()->{if(order!=null)rentals.assertAccess(order);var id=UUID.randomUUID();r.create(id,order,subject,message,actor.username());audit.record("SUPPORT_CREATED",id,subject);return id;});}
    @Transactional public void resolve(UUID id,String resolution,String key){actor.require("OWNER","MANAGER");Checks.text(resolution,2000,"Kết quả xử lý");commands.once(key,"SUPPORT_RESOLVE:"+id+":"+resolution,()->{var t=r.ticket(id);if(t==null)throw DomainException.missing();if(!t.get("status").equals("OPEN"))throw DomainException.conflict("Yêu cầu đã được xử lý.");r.resolve(id,resolution,actor.username());audit.record("SUPPORT_RESOLVED",id,resolution);return id;});}
    public List<Map<String,Object>> notifications(){actor.require("OWNER","MANAGER");return r.notifications();}
    public void deliverNotifications(){for(var event:r.claim()){UUID id=(UUID)event.get("id");int attempt=((Number)event.get("attempts")).intValue();try{port.send(id,(String)event.get("event_type"),(String)event.get("payload"));r.sent(id,attempt);}catch(Exception ex){r.failed(id,attempt);}}}
}
