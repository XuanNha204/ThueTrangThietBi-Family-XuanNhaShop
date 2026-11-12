package vn.xuannhashop.business.reporting.application;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.business.shared.security.ActorContext;
@Service
public class EquipmentHistoryService implements EquipmentHistoryApplication {
    private final EquipmentHistoryRepository r;private final ActorContext actor;
    public EquipmentHistoryService(EquipmentHistoryRepository r,ActorContext actor){this.r=r;this.actor=actor;}
    @Transactional(readOnly=true) public Map<String,Object> history(UUID id,Instant before,String beforeKey){
        actor.require("OWNER","MANAGER","INVENTORY","TECHNICIAN");
        if((before==null)!=(beforeKey==null)||(beforeKey!=null&&(beforeKey.isBlank()||beforeKey.length()>100)))throw DomainException.bad("Mốc tải lịch sử không hợp lệ.");
        var equipment=r.equipment(id);if(equipment==null)throw DomainException.missing();
        var rows=r.events(id,before,beforeKey);boolean more=rows.size()>50;var page=rows.stream().limit(50).toList();
        var result=new LinkedHashMap<String,Object>();result.put("equipment",equipment);result.put("events",page);
        result.put("nextCursor",more?Map.of("before",page.get(49).get("event_at"),"beforeKey",page.get(49).get("event_key")):null);return result;
    }
}
