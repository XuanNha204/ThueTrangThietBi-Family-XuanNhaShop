package vn.xuannhashop.infrastructure.persistence;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.reporting.application.EquipmentHistoryRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcEquipmentHistoryRepository implements EquipmentHistoryRepository {
    private final Sql s;public JdbcEquipmentHistoryRepository(Sql s){this.s=s;}
    public Map<String,Object> equipment(UUID id){return s.one("select e.id,e.code,e.type_id,e.state,t.name,w.name warehouse from equipment e join equipment_type t on t.id=e.type_id join warehouse w on w.id=e.warehouse_id where e.id=:id",p("id",id));}
    public List<Map<String,Object>> events(UUID id,Instant before,String beforeKey){
        var rows=s.rows("""
            with events as (
              select 'INSPECTION:'||i.id event_key,'INSPECTION' kind,i.id object_id,i.created_at event_at,
                i.inspected_by actor,i.findings summary,i.passed,i.type_checklist_version checklist_version,
                i.results_recorded,null::text order_code,null::text order_status
              from inspection i where i.equipment_id=:id
              union all select 'FAULT_OPEN:'||m.id,'FAULT_OPEN',m.id,m.opened_at,m.opened_by,m.fault,null,null,false,null,null
              from maintenance_ticket m where m.equipment_id=:id
              union all select 'FAULT_CLOSED:'||m.id,'FAULT_CLOSED',m.id,m.closed_at,null,m.fault,null,null,false,null,null
              from maintenance_ticket m where m.equipment_id=:id and m.closed_at is not null
              union all select 'PLAN_SCHEDULED:'||m.id,'PLAN_SCHEDULED',m.id,m.created_at,m.created_by,m.reason,null,null,false,null,null
              from maintenance_plan m where m.equipment_id=:id
              union all select 'PLAN_STARTED:'||m.id,'PLAN_STARTED',m.id,m.started_at,m.started_by,m.reason,null,null,false,null,null
              from maintenance_plan m where m.equipment_id=:id and m.started_at is not null
              union all select 'PLAN_'||m.status||':'||m.id,'PLAN_'||m.status,m.id,m.completed_at,m.completed_by,m.completion_note,null,null,false,null,null
              from maintenance_plan m where m.equipment_id=:id and m.completed_at is not null
              union all select 'RENTAL_BOOKED:'||o.id,'RENTAL_BOOKED',o.id,o.created_at,o.created_by,
                'Thời điểm lập đơn; thiết bị từng được giữ hoặc cấp cho đơn này.',null,null,false,o.code,o.status
              from rental_order o where exists(select 1 from reservation r where r.order_id=o.id and r.equipment_id=:id)
                or exists(select 1 from rental_item i where i.order_id=o.id and i.equipment_id=:id)
              union all select 'HANDED_OVER:'||i.id,'HANDED_OVER',i.id,i.handed_at,null,null,null,null,false,o.code,o.status
              from rental_item i join rental_order o on o.id=i.order_id where i.equipment_id=:id and i.handed_at is not null
              union all select 'RETURNED:'||i.id,'RETURNED',i.id,i.returned_at,null,i.return_condition,null,null,false,o.code,o.status
              from rental_item i join rental_order o on o.id=i.order_id where i.equipment_id=:id and i.returned_at is not null
              union all select 'RESOLVED:'||i.id,'RESOLVED',i.id,i.resolved_at,null,i.resolution_kind,null,null,false,o.code,o.status
              from rental_item i join rental_order o on o.id=i.order_id where i.equipment_id=:id and i.resolved_at is not null
            ) select * from events where cast(:before as timestamptz) is null or (event_at,event_key)<(cast(:before as timestamptz),cast(:key as text))
            order by event_at desc,event_key desc limit 51
            """,p("id",id,"before",before,"key",beforeKey));
        for(var row:rows)if("INSPECTION".equals(row.get("kind")))row.put("results",s.rows("select code,label,blocking,passed,note from inspection_check_result where inspection_id=:id order by position",p("id",row.get("object_id"))));
        return rows;
    }
}
