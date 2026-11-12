package vn.xuannhashop.business.maintenance.application;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.reservation.application.ReservationApplication;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class MaintenancePlanningService implements MaintenancePlanningApplication {
    private final MaintenancePlanningRepository r;private final ReservationApplication reservation;
    private final EquipmentApplication equipment;private final MaintenanceApplication maintenance;
    private final ActorContext actor;private final Commands commands;private final ActivityService audit;private final Clock clock;
    public MaintenancePlanningService(MaintenancePlanningRepository r,ReservationApplication reservation,EquipmentApplication equipment,MaintenanceApplication maintenance,ActorContext actor,Commands commands,ActivityService audit,Clock clock){this.r=r;this.reservation=reservation;this.equipment=equipment;this.maintenance=maintenance;this.actor=actor;this.commands=commands;this.audit=audit;this.clock=clock;}
    public List<Map<String,Object>> plans(){actor.require("OWNER","MANAGER","TECHNICIAN","INVENTORY");return r.plans();}
    @Transactional public UUID schedule(UUID asset,Instant from,Instant to,String reason,String key){
        actor.require("OWNER","TECHNICIAN");String note=Checks.text(reason,1000,"Nội dung bảo trì");
        return commands.once(key,"MAINT_PLAN:"+asset+":"+from+":"+to+":"+note,()->{if(from==null||to==null||from.isBefore(clock.instant())||!to.isAfter(from))throw DomainException.bad("Chọn lịch bảo trì tương lai hợp lệ.");UUID id=UUID.randomUUID();reservation.blockMaintenance(id,asset,from,to);r.schedule(id,asset,from,to,note,actor.username());audit.record("MAINTENANCE_SCHEDULED",asset,"plan="+id+"; "+from+" -> "+to+"; "+note);return id;});
    }
    private Map<String,Object> plan(UUID id,String expected){var p=r.lock(id);if(p==null)throw DomainException.missing();if(!p.get("status").equals(expected))throw DomainException.conflict("Trạng thái kế hoạch đã thay đổi.");return p;}
    @Transactional public void start(UUID id,String key){actor.require("OWNER","TECHNICIAN");commands.once(key,"MAINT_START:"+id,()->{
        var p=plan(id,"SCHEDULED");if(clock.instant().isBefore((Instant)p.get("starts_at")))throw DomainException.conflict("Chưa đến giờ bắt đầu bảo trì.");
        UUID asset=(UUID)p.get("equipment_id");equipment.transition(asset,Set.of("READY","AWAIT_INSPECTION","AWAIT_ACCEPTANCE","MAINTENANCE"),"MAINTENANCE");r.start(id,actor.username());audit.record("MAINTENANCE_STARTED",asset,"plan="+id);return id;
    });}
    @Transactional public void cancel(UUID id,String reason,String key){actor.require("OWNER","TECHNICIAN");String note=Checks.text(reason,1000,"Lý do hủy bảo trì");commands.once(key,"MAINT_CANCEL:"+id+":"+note,()->{
        var p=plan(id,"SCHEDULED");reservation.releaseMaintenance(id,(UUID)p.get("equipment_id"));r.finish(id,"CANCELLED",note,actor.username(),null);audit.record("MAINTENANCE_PLAN_CANCELLED",(UUID)p.get("equipment_id"),"plan="+id+"; "+note);return id;
    });}
    @Transactional public void complete(UUID id,boolean safe,boolean clean,boolean complete,String findings,String key){complete(id,new InspectionChecklistApplication.Input(safe,clean,complete,findings,null,List.of()),key);}
    @Transactional public void complete(UUID id,InspectionChecklistApplication.Input input,String key){actor.require("OWNER","TECHNICIAN");if(input==null)throw DomainException.bad("Thiếu kết quả kiểm tra.");String note=Checks.text(input.findings(),1000,"Kết quả bảo trì");
        boolean legacy=input.checklistVersion()==null&&(input.answers()==null||input.answers().isEmpty());String payload=legacy?"MAINT_COMPLETE:"+id+":"+input.powerSafe()+":"+input.clean()+":"+input.complete()+":"+note:"MAINT_COMPLETE:"+id+":"+input;
        commands.once(key,payload,()->{
        var p=plan(id,"IN_PROGRESS");UUID asset=(UUID)p.get("equipment_id");UUID inspection=maintenance.inspect(asset,input,"plan-inspect:"+id);
        reservation.releaseMaintenance(id,asset);r.finish(id,"COMPLETED",note,actor.username(),inspection);audit.record("MAINTENANCE_PLAN_COMPLETED",asset,"plan="+id+"; inspection="+inspection+"; state="+equipment.asset(asset).state());return id;
    });}
}
