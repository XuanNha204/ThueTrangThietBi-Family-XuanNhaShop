package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.maintenance.application.MaintenanceRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcMaintenanceRepository implements MaintenanceRepository {
    private final Sql s;public JdbcMaintenanceRepository(Sql s){this.s=s;}
    public List<Map<String,Object>> inspections(){return s.rows("select i.*,e.code equipment_code,t.name from inspection i join equipment e on e.id=i.equipment_id join equipment_type t on t.id=e.type_id order by i.created_at desc limit 200",Map.of());}
    public List<Map<String,Object>> tickets(){return s.rows("select m.*,e.code equipment_code,t.name from maintenance_ticket m join equipment e on e.id=m.equipment_id join equipment_type t on t.id=e.type_id order by m.opened_at desc limit 200",Map.of());}
    public void inspect(UUID id,UUID e,boolean passed,String note,String actor){s.update("insert into inspection(id,equipment_id,checklist_version,passed,findings,inspected_by) values(:id,:e,'DEV-1',:passed,:note,:actor)",p("id",id,"e",e,"passed",passed,"note",note,"actor",actor));}
    public void open(UUID id,UUID e,String fault,String actor){s.update("insert into maintenance_ticket(id,equipment_id,fault,status,opened_by) values(:id,:e,:fault,'OPEN',:actor)",p("id",id,"e",e,"fault",fault,"actor",actor));}
    public void close(UUID e){s.update("update maintenance_ticket set status='CLOSED',closed_at=now() where equipment_id=:e and status='OPEN'",p("e",e));}
    public boolean assessedAfter(UUID e,java.time.Instant at){return s.one("select 1 from inspection where equipment_id=:e and created_at>=:at limit 1",p("e",e,"at",at))!=null;}
}
