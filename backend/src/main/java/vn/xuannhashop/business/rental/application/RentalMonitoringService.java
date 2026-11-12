package vn.xuannhashop.business.rental.application;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class RentalMonitoringService implements RentalMonitoringApplication {
    private final RentalRepository r;private final RentalApplication rentals;
    private final ActorContext actor;private final ActivityService audit;private final int reminderHours;
    public RentalMonitoringService(RentalRepository r,RentalApplication rentals,ActorContext actor,ActivityService audit,@Value("${app.reminder-hours:24}")int hours){this.r=r;this.rentals=rentals;this.actor=actor;this.audit=audit;reminderHours=hours;}
    public List<Map<String,Object>> attention(){actor.require("OWNER","MANAGER","INVENTORY","DELIVERY","TECHNICIAN");return r.attention();}
    public List<Map<String,Object>> notices(UUID order){rentals.assertAccess(order);return r.notices(order,actor.current().role().equals("CUSTOMER"));}
    @Transactional public void monitor(){
        for(var row:r.due(reminderHours)){
            UUID order=(UUID)row.get("id");String event=(String)row.get("event_type");
            if(audit.notifyOnce(order,event,row.get("code")+" · Giờ trả theo thỏa thuận: "+row.get("ends_at"),event+":"+order+":"+((java.time.Instant)row.get("ends_at")).toEpochMilli(),"CUSTOMER"))audit.system(event,order,"Nhắc theo giờ trả của phiên bản thỏa thuận hiện hành; kênh dev cục bộ.");
        }
        for(var row:r.attention())if(!row.get("risk").equals("OVERDUE")){
            UUID order=(UUID)row.get("order_id");
            audit.notifyOnce(order,"SCHEDULE_AT_RISK",row.get("code")+" · "+row.get("equipment_code")+" · "+row.get("risk"),"RISK:"+order+":"+row.get("item_id")+":"+row.get("risk")+":"+row.get("ends_at"),"STAFF");
        }
    }
}
