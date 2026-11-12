package vn.xuannhashop.business.maintenance.application;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.shared.security.ActorContext;
import vn.xuannhashop.business.rules.*;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
@Service
public class MaintenanceService implements MaintenanceApplication {
    private final MaintenanceRepository r;private final EquipmentApplication equipment;private final ActorContext actor;private final ActivityService audit;private final Commands commands;private final RuleEngine engine;private final Map<String,InspectionRule> rules;
    private final InspectionChecklistRepository checklists;
    public MaintenanceService(MaintenanceRepository r,EquipmentApplication e,ActorContext a,ActivityService au,Commands c,RuleEngine en,Map<String,InspectionRule> rules,InspectionChecklistRepository checklists){this.r=r;equipment=e;actor=a;audit=au;commands=c;engine=en;this.rules=rules;this.checklists=checklists;}
    public List<Map<String,Object>> inspections(){actor.require("OWNER","MANAGER","TECHNICIAN","INVENTORY");return r.inspections();}
    public List<Map<String,Object>> tickets(){actor.require("OWNER","MANAGER","TECHNICIAN","INVENTORY");return r.tickets();}
    public boolean assessedAfter(UUID equipment,java.time.Instant returnedAt){return returnedAt!=null&&r.assessedAfter(equipment,returnedAt);}
    @Transactional public UUID inspect(UUID e,boolean safe,boolean clean,boolean complete,String findings,String key){return inspect(e,new InspectionChecklistApplication.Input(safe,clean,complete,findings,null,List.of()),key);}
    @Transactional public UUID inspect(UUID e,InspectionChecklistApplication.Input input,String key){
        actor.require("OWNER","TECHNICIAN");if(input==null)throw DomainException.bad("Thiếu kết quả kiểm tra.");String findings=Checks.text(input.findings(),1000,"Kết quả kiểm tra");
        boolean legacy=input.checklistVersion()==null&&(input.answers()==null||input.answers().isEmpty());
        String payload=legacy?"INSPECT:"+e+":"+input.powerSafe()+":"+input.clean()+":"+input.complete()+":"+input.findings():"INSPECT:"+e+":"+input;
        return commands.once(key,payload,()->{
            var asset=equipment.asset(e);var template=checklists.current(asset.typeId(),true);
            if((input.checklistVersion()==null&&template.version()!=0)||(input.checklistVersion()!=null&&input.checklistVersion()!=template.version()))throw DomainException.conflict("Checklist đã thay đổi. Đóng cửa sổ, mở lại và kiểm tra theo phiên bản hiện hành.");
            var answers=input.answers()==null?List.<InspectionChecklistApplication.Answer>of():input.answers();var byCode=new HashMap<String,InspectionChecklistApplication.Answer>();
            if(answers.size()!=template.checks().size())throw DomainException.bad("Phải trả lời đầy đủ các tiêu chí bổ sung của checklist.");
            for(var answer:answers){if(answer==null||answer.code()==null||answer.passed()==null||byCode.put(answer.code(),answer)!=null)throw DomainException.bad("Kết quả tiêu chí thiếu, trùng hoặc không hợp lệ.");if(answer.note()!=null&&answer.note().length()>500)throw DomainException.bad("Ghi chú tiêu chí tối đa 500 ký tự.");}
            var results=new ArrayList<InspectionChecklistApplication.Result>();
            results.add(new InspectionChecklistApplication.Result("BASE_SAFE","Vận hành và kết cấu an toàn",true,input.powerSafe(),""));
            results.add(new InspectionChecklistApplication.Result("BASE_CLEAN","Đã làm sạch, vệ sinh phù hợp",true,input.clean(),""));
            results.add(new InspectionChecklistApplication.Result("BASE_COMPLETE","Đủ phụ kiện và đã đối chiếu",true,input.complete(),""));
            for(var criterion:template.checks()){var answer=byCode.remove(criterion.code());if(answer==null)throw DomainException.bad("Thiếu kết quả cho tiêu chí "+criterion.code()+".");String note=answer.note()==null?"":answer.note().strip();if(!answer.passed()&&note.isBlank())throw DomainException.bad("Cần ghi rõ lý do tiêu chí chưa đạt: "+criterion.label());results.add(new InspectionChecklistApplication.Result(criterion.code(),criterion.label(),criterion.blocking(),answer.passed(),note));}
            boolean passed=engine.select(rules,"inspectionDEV1").safe(input.powerSafe(),input.clean(),input.complete())&&results.stream().filter(InspectionChecklistApplication.Result::blocking).allMatch(InspectionChecklistApplication.Result::passed);
            equipment.transition(e,Set.of("AWAIT_ACCEPTANCE","AWAIT_INSPECTION","MAINTENANCE","SUSPENDED","READY"),passed?"READY":"MAINTENANCE");
            var id=UUID.randomUUID();r.inspect(id,e,passed,findings,actor.username());checklists.results(id,template.version(),results);
            if(passed)r.close(e);else r.open(UUID.randomUUID(),e,findings,actor.username());
            audit.record("INSPECTION_RECORDED",e,"DEV-1; checklist="+template.version()+"; passed="+passed+"; "+findings);return id;
        });
    }
    @Transactional public UUID open(UUID e,String fault,String key){actor.require("OWNER","TECHNICIAN","DELIVERY");Checks.text(fault,1000,"Sự cố");return commands.once(key,"MAINTENANCE:"+e+":"+fault,()->{equipment.transition(e,Set.of("AWAIT_ACCEPTANCE","AWAIT_INSPECTION","SUSPENDED","READY"),"MAINTENANCE");var id=UUID.randomUUID();r.open(id,e,fault,actor.username());audit.record("MAINTENANCE_OPENED",e,fault);return id;});}
}
