package vn.xuannhashop.business.rental.application;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.finance.application.FinanceApplication;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.maintenance.application.MaintenanceApplication;
import vn.xuannhashop.business.reservation.application.ReservationApplication;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
@Transactional
public class RentalIncidentService implements RentalIncidentApplication {
    private final RentalRepository r;private final RentalApplication rentals;private final RentalIncidentRepository incidents;
    private final EquipmentApplication equipment;private final ReservationApplication schedules;private final FinanceApplication finance;
    private final ActorContext actor;private final ActivityService audit;private final Commands commands;
    private final MaintenanceApplication maintenance;
    public RentalIncidentService(RentalRepository r,RentalApplication rentals,RentalIncidentRepository incidents,EquipmentApplication equipment,ReservationApplication schedules,FinanceApplication finance,ActorContext actor,ActivityService audit,Commands commands,MaintenanceApplication maintenance){this.r=r;this.rentals=rentals;this.incidents=incidents;this.equipment=equipment;this.schedules=schedules;this.finance=finance;this.actor=actor;this.audit=audit;this.commands=commands;this.maintenance=maintenance;}
    public List<Map<String,Object>> list(UUID order){rentals.assertAccess(order);return incidents.list(order);}
    private Map<String,Object> item(UUID order,UUID item){return r.items(order).stream().filter(i->i.get("id").equals(item)).findFirst().orElseThrow(DomainException::missing);}
    private void eligible(UUID order,Report report){
        var o=r.get(order,true);if(o==null)throw DomainException.missing();if(!Set.of("RENTED","PARTIAL_RETURN","AWAIT_SETTLEMENT").contains(o.status()))throw DomainException.conflict("Chỉ ghi sự cố khi đã bàn giao và chưa tất toán.");
        var line=item(order,report.itemId());
        if(line.get("handed_at")==null||line.get("resolved_at")!=null)throw DomainException.conflict("Dòng thiết bị chưa giao hoặc đã được xử lý mất.");
        if(report.kind().equals("LOST")&&line.get("returned_at")!=null)throw DomainException.conflict("Thiết bị đã nhận trả không thể xử lý là mất.");
        if(report.kind().equals("DAMAGE")&&(line.get("returned_at")==null||!"DAMAGED".equals(line.get("return_condition"))))throw DomainException.conflict("Hư hỏng cần có biên bản thu hồi ghi nhận DAMAGED.");
    }
    public UUID report(UUID order,Report report,String key){actor.require("OWNER","MANAGER","INVENTORY","DELIVERY","TECHNICIAN");
        if(report==null||report.kind()==null||!Set.of("LOST","DAMAGE").contains(report.kind())||report.itemId()==null)throw DomainException.bad("Loại sự cố không hợp lệ.");
        if(report.amount()==null||report.amount().signum()<0||report.amount().scale()>2||report.amount().compareTo(new BigDecimal("999999999999.99"))>0)throw DomainException.bad("Mức bồi thường thỏa thuận phải không âm, tối đa hai số thập phân.");
        Checks.text(report.description(),2000,"Diễn biến sự cố");Checks.text(report.customerName(),120,"Khách xác nhận");Checks.text(report.agreementBasis(),2000,"Căn cứ thỏa thuận xử lý");
        return commands.once(key,"INCIDENT_REPORT:"+order+":"+report,()->{eligible(order,report);UUID id=UUID.randomUUID();incidents.create(id,order,report,actor.username());audit.record("INCIDENT_REPORTED",order,"incident="+id+" / "+report.kind());return id;});
    }
    public void decide(UUID order,UUID id,boolean approved,String note,String key){actor.require("OWNER","ACCOUNTANT");commands.once(key,"INCIDENT_DECIDE:"+order+":"+id+":"+approved+":"+note,()->{
        var o=r.get(order,true);if(o==null)throw DomainException.missing();var incident=incidents.get(id,true);
        if(incident==null||!incident.get("order_id").equals(order))throw DomainException.missing();
        if(!incident.get("status").equals("PENDING"))throw DomainException.conflict("Sự cố đã có quyết định.");
        if(incident.get("requested_by").equals(actor.username()))throw DomainException.forbidden();Checks.text(note,1000,"Căn cứ quyết định");
        if(approved){
            UUID itemId=(UUID)incident.get("item_id");String kind=(String)incident.get("kind");
            eligible(order,new Report(itemId,kind,(BigDecimal)incident.get("amount"),"","",""));
            var line=item(order,itemId);
            if(kind.equals("DAMAGE")&&!maintenance.assessedAfter((UUID)line.get("equipment_id"),(java.time.Instant)line.get("returned_at")))throw DomainException.conflict("Kỹ thuật cần kiểm tra thiết bị sau thu hồi trước khi duyệt phương án hư hỏng.");
            if(kind.equals("LOST")){
                equipment.transition((UUID)line.get("equipment_id"),Set.of("RENTED"),"SUSPENDED");schedules.releaseItem(itemId);r.resolveLost(itemId);
                r.status(order,r.items(order).stream().allMatch(i->i.get("returned_at")!=null||i.get("resolved_at")!=null)?"AWAIT_SETTLEMENT":"PARTIAL_RETURN");
                if(r.items(order).stream().allMatch(i->i.get("returned_at")!=null||i.get("resolved_at")!=null))r.closePendingChanges(order,"Nghĩa vụ thiết bị đã được xử lý theo quyết định.");
            }
            finance.addCharge(order,id,(BigDecimal)incident.get("amount"),(String)incident.get("description"));
        }
        incidents.decide(id,approved,actor.username(),note);audit.record(approved?"INCIDENT_APPROVED":"INCIDENT_REJECTED",order,"incident="+id);audit.notifyOrder(order,"INCIDENT_DECIDED",o.code()+" / "+(approved?"Đã duyệt":"Từ chối"));return id;
    });}
}
