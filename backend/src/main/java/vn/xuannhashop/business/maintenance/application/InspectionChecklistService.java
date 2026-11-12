package vn.xuannhashop.business.maintenance.application;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class InspectionChecklistService implements InspectionChecklistApplication {
    private final InspectionChecklistRepository r;private final EquipmentApplication equipment;private final ActorContext actor;private final Commands commands;private final ActivityService audit;
    public InspectionChecklistService(InspectionChecklistRepository r,EquipmentApplication equipment,ActorContext actor,Commands commands,ActivityService audit){this.r=r;this.equipment=equipment;this.actor=actor;this.commands=commands;this.audit=audit;}
    public Template current(UUID type){actor.require("OWNER","MANAGER","INVENTORY","TECHNICIAN");equipment.type(type);return r.current(type,false);}
    public List<Map<String,Object>> history(UUID type){actor.require("OWNER","MANAGER","INVENTORY","TECHNICIAN");equipment.type(type);return r.history(type);}
    @Transactional public UUID publish(UUID type,Publish input,String key){
        actor.require("OWNER");equipment.type(type);
        if(input==null||input.expectedVersion()==null||input.expectedVersion()<0||input.checks()==null||input.checks().size()>20)throw DomainException.bad("Checklist cần phiên bản hiện tại và tối đa 20 tiêu chí bổ sung.");
        String reason=Checks.text(input.reason(),500,"Căn cứ áp dụng checklist");var codes=new HashSet<String>();var checks=new ArrayList<Criterion>();
        for(var c:input.checks()){
            if(c==null||c.code()==null||!c.code().matches("[A-Z][A-Z0-9_]{0,39}")||c.code().startsWith("BASE_")||!codes.add(c.code())||c.blocking()==null)throw DomainException.bad("Mã tiêu chí phải duy nhất, dùng chữ hoa/số/gạch dưới, không bắt đầu BASE_; cần chỉ rõ tiêu chí bắt buộc.");
            checks.add(new Criterion(c.code(),Checks.text(c.label(),200,"Nội dung tiêu chí"),c.blocking()));
        }
        return commands.once(key,"CHECKLIST_PUBLISH:"+type+":"+input.expectedVersion()+":"+checks+":"+reason,()->{
            var current=r.current(type,true);if(current.version()!=input.expectedVersion())throw DomainException.conflict("Checklist đã thay đổi. Đóng cửa sổ rồi mở lại để lấy phiên bản mới.");
            if(current.checks().equals(checks))throw DomainException.bad("Checklist chưa thay đổi.");
            UUID id=UUID.randomUUID();r.publish(id,type,current.version()+1,checks,reason,actor.username());audit.record("INSPECTION_CHECKLIST_PUBLISHED",type,"v"+current.version()+" → v"+(current.version()+1)+"; "+reason);return id;
        });
    }
}
