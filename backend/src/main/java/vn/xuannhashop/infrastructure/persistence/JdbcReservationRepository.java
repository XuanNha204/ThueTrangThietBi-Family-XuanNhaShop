package vn.xuannhashop.infrastructure.persistence;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.reservation.application.ReservationRepository;
import vn.xuannhashop.business.reservation.application.ReservationApplication.Asset;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcReservationRepository implements ReservationRepository {
    private final Sql s;public JdbcReservationRepository(Sql s){this.s=s;}
    public List<Asset> candidates(List<UUID> types,boolean lock){return s.rows("select e.* from equipment e where e.type_id in (:types) and e.state in ('READY','PREPARING','RENTED') order by e.id"+(lock?" for update of e":""),p("types",types)).stream().map(r->new Asset((UUID)r.get("id"),(String)r.get("code"),(UUID)r.get("type_id"),(String)r.get("state"))).toList();}
    public boolean overlaps(UUID equipment,Instant from,Instant to){return overlapsExcept(equipment,from,to,null);}
    public boolean overlapsExcept(UUID equipment,Instant from,Instant to,UUID order){
        return s.one("""
            select 1 where exists (
              select 1 from reservation where equipment_id=:id
              and (cast(:o as uuid) is null or order_id<>:o)
              and (status='ALLOCATED' or (status='HELD' and expires_at>clock_timestamp()))
              and occupied_from<:to and occupied_until>:from
            ) or exists (
              select 1 from maintenance_window w where w.equipment_id=:id and w.active
              and w.occupied_from<:to and (w.occupied_until>:from or w.occupied_from<=clock_timestamp())
            ) or exists (
              select 1 from rental_item i join rental_order o on o.id=i.order_id
              where i.equipment_id=:id and i.handed_at is not null and i.returned_at is null
              and i.resolved_at is null and (o.ends_at<=clock_timestamp() or exists(select 1 from rental_incident x where x.item_id=i.id and x.kind='LOST' and x.status='PENDING'))
              and (cast(:o as uuid) is null or o.id<>:o)
            )
            """,p("id",equipment,"from",from,"to",to,"o",order))!=null;
    }
    public void hold(UUID o,UUID i,UUID e,Instant from,Instant to,Instant expires){insertAllocated(o,i,e,from,to,expires,"HELD");}
    public void insertAllocated(UUID o,UUID i,UUID e,Instant from,Instant to,Instant expires,String status){
        s.update("insert into reservation(id,order_id,item_id,equipment_id,occupied_from,occupied_until,status,expires_at) values(:id,:o,:i,:e,:f,:t,:status,:x)",p("id",UUID.randomUUID(),"o",o,"i",i,"e",e,"f",from,"t",to,"x",expires,"status",status));
    }
    public void confirm(UUID o){
        var rows=s.rows("select e.*,r.occupied_from,r.occupied_until from equipment e join reservation r on r.equipment_id=e.id where r.order_id=:o and r.status='HELD' order by e.id for update of e",p("o",o));
        if(rows.isEmpty())throw vn.xuannhashop.business.shared.DomainException.conflict("Giữ chỗ không còn hiệu lực.");
        for(var row:rows)if(s.one("select 1 from reservation where order_id=:o and equipment_id=:e and status='HELD' and expires_at>clock_timestamp()",p("o",o,"e",row.get("id")))==null
            ||!Set.of("READY","PREPARING","RENTED").contains(row.get("state"))
            ||overlapsExcept((UUID)row.get("id"),(Instant)row.get("occupied_from"),(Instant)row.get("occupied_until"),o))
            throw vn.xuannhashop.business.shared.DomainException.conflict("Giữ chỗ hết hiệu lực, xung đột hoặc thiết bị đang bị trả muộn. Cần đối soát cọc nếu đã nhận.");
        s.update("update reservation set status='ALLOCATED' where order_id=:o and status='HELD'",p("o",o));
    }
    public void release(UUID o){s.update("update reservation set status='RELEASED' where order_id=:o",p("o",o));}
    public void releaseItem(UUID i){s.update("update reservation set status='RELEASED' where item_id=:i",p("i",i));}
    public void lock(UUID e){s.rows("select id from equipment where id=:id for update",p("id",e));}
    public Asset lockAsset(UUID id){var r=s.one("select * from equipment where id=:id for update",p("id",id));return r==null?null:new Asset((UUID)r.get("id"),(String)r.get("code"),(UUID)r.get("type_id"),(String)r.get("state"));}
    public void blockMaintenance(UUID window,UUID equipment,Instant from,Instant to){s.update("insert into maintenance_window(id,equipment_id,occupied_from,occupied_until) values(:id,:e,:f,:t)",p("id",window,"e",equipment,"f",from,"t",to));}
    public void releaseMaintenance(UUID window,UUID equipment){s.update("update maintenance_window set active=false where id=:id and equipment_id=:e",p("id",window,"e",equipment));}
    public List<Asset> lockChange(UUID order,List<UUID> equipment){return s.rows("select e.* from equipment e where e.id in (:ids) or e.id in (select equipment_id from reservation where order_id=:o and status<>'RELEASED') order by e.id for update of e",p("ids",equipment,"o",order)).stream().map(row->new Asset((UUID)row.get("id"),(String)row.get("code"),(UUID)row.get("type_id"),(String)row.get("state"))).toList();}
    public boolean committed(UUID id){return s.one("select 1 where exists(select 1 from reservation where equipment_id=:id and (status='ALLOCATED' or (status='HELD' and expires_at>clock_timestamp()))) or exists(select 1 from rental_item where equipment_id=:id and handed_at is not null and returned_at is null and resolved_at is null) or exists(select 1 from maintenance_window where equipment_id=:id and active) or exists(select 1 from warranty_case where equipment_id=:id and status='OPEN')",p("id",id))!=null;}
}
