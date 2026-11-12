package vn.xuannhashop.infrastructure.persistence;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.maintenance.application.MaintenancePlanningRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcMaintenancePlanningRepository implements MaintenancePlanningRepository {
    private final Sql s;public JdbcMaintenancePlanningRepository(Sql s){this.s=s;}
    public List<Map<String,Object>> plans(){return s.rows("select p.*,e.type_id,e.code equipment_code,t.name,i.passed,case when p.status='SCHEDULED' and p.starts_at<=clock_timestamp() then true else false end overdue_start from maintenance_plan p join equipment e on e.id=p.equipment_id join equipment_type t on t.id=e.type_id left join inspection i on i.id=p.inspection_id order by case when p.status in ('SCHEDULED','IN_PROGRESS') then 0 else 1 end,p.starts_at desc limit 300",Map.of());}
    public Map<String,Object> lock(UUID id){return s.one("select * from maintenance_plan where id=:id for update",p("id",id));}
    public void schedule(UUID id,UUID e,Instant from,Instant to,String reason,String actor){s.update("insert into maintenance_plan(id,equipment_id,starts_at,ends_at,reason,created_by) values(:id,:e,:f,:t,:r,:a)",p("id",id,"e",e,"f",from,"t",to,"r",reason,"a",actor));}
    public void start(UUID id,String actor){s.update("update maintenance_plan set status='IN_PROGRESS',started_by=:a,started_at=clock_timestamp() where id=:id",p("id",id,"a",actor));}
    public void finish(UUID id,String status,String note,String actor,UUID inspection){s.update("update maintenance_plan set status=:s,completion_note=:n,completed_by=:a,completed_at=clock_timestamp(),inspection_id=:i where id=:id",p("id",id,"s",status,"n",note,"a",actor,"i",inspection));}
}
