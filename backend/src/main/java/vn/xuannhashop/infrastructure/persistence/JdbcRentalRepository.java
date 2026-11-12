package vn.xuannhashop.infrastructure.persistence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.rental.application.RentalRepository;
import vn.xuannhashop.business.rental.domain.RentalOrder;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcRentalRepository implements RentalRepository {
    private final Sql s;public JdbcRentalRepository(Sql s){this.s=s;}
    public String nextCode(){return "XN-"+String.format("%06d",((Number)s.one("select nextval('rental_code_seq') n",Map.of()).get("n")).longValue());}
    public void create(RentalOrder o,String actor){s.update("insert into rental_order(id,code,customer_id,status,starts_at,ends_at,expires_at,rental_total,deposit_required,address,terms,agreement_version,created_by) values(:id,:code,:customer,:status,:f,:t,:x,:rent,:deposit,:address,:terms,:policy,:actor)",p("id",o.id(),"code",o.code(),"customer",o.customerId(),"status",o.status(),"f",o.startsAt(),"t",o.endsAt(),"x",o.expiresAt(),"rent",o.rentalTotal(),"deposit",o.depositRequired(),"address",o.address(),"terms",o.terms(),"policy",o.agreementVersion(),"actor",actor));s.update("insert into agreement_revision(id,order_id,revision,terms,accepted_by) values(:id,:order,1,:terms,:actor)",p("id",UUID.randomUUID(),"order",o.id(),"terms",o.terms(),"actor",actor));}
    public void item(UUID id,UUID order,UUID equipment,String name,BigDecimal daily,BigDecimal rent,BigDecimal deposit,String policy){s.update("insert into rental_item(id,order_id,equipment_id,type_name,daily_rate,rental_amount,deposit_amount,policy_version) values(:id,:o,:e,:n,:d,:r,:c,:p)",p("id",id,"o",order,"e",equipment,"n",name,"d",daily,"r",rent,"c",deposit,"p",policy));}
    public RentalOrder get(UUID id,boolean lock){var r=s.one("select o.*,c.name customer_name from rental_order o join customer c on c.id=o.customer_id where o.id=:id"+(lock?" for update of o":""),p("id",id));if(r==null)return null;return new RentalOrder(id,(String)r.get("code"),(UUID)r.get("customer_id"),(String)r.get("customer_name"),(String)r.get("status"),(Instant)r.get("starts_at"),(Instant)r.get("ends_at"),(Instant)r.get("expires_at"),(BigDecimal)r.get("rental_total"),(BigDecimal)r.get("deposit_required"),(String)r.get("address"),(String)r.get("terms"),(String)r.get("agreement_version"),((Number)r.get("version")).longValue());}
    public List<Map<String,Object>> list(UUID customer){return s.rows("select o.id,o.code,o.status,o.starts_at,o.ends_at,o.expires_at,o.rental_total,o.deposit_required,o.created_at,c.name customer_name,(select count(*) from rental_item i where i.order_id=o.id) item_count from rental_order o join customer c on c.id=o.customer_id"+(customer==null?"":" where o.customer_id=:customer")+" order by o.created_at desc limit 300",p("customer",customer));}
    public List<Map<String,Object>> items(UUID order){return s.rows("select i.*,exists(select 1 from rental_incident x where x.item_id=i.id and x.status='PENDING') pending_incident,e.type_id,e.code equipment_code,e.state equipment_state from rental_item i join equipment e on e.id=i.equipment_id where i.order_id=:o order by e.code",p("o",order));}
    public void status(UUID order,String status){s.update("update rental_order set status=:s,version=version+1 where id=:id",p("id",order,"s",status));}
    public void handover(UUID item,Instant at){s.update("update rental_item set handed_at=:at where id=:id",p("id",item,"at",at));}
    public void returned(UUID item,Instant at,String condition){s.update("update rental_item set returned_at=:at,return_condition=:c where id=:id",p("id",item,"at",at,"c",condition));}
    public List<UUID> expired(){return s.rows("select id from rental_order where status='HELD' and expires_at<=clock_timestamp() order by id limit 50 for update skip locked",Map.of()).stream().map(r->(UUID)r.get("id")).toList();}
    public void captureInitialAgreement(UUID order){s.update("""
        update agreement_revision a set snapshot=jsonb_build_object(
          'startsAt',o.starts_at,'endsAt',o.ends_at,'address',o.address,
          'rentalTotal',o.rental_total,'depositRequired',o.deposit_required,
          'customerName',(select name from customer where id=o.customer_id),
          'items',(select coalesce(jsonb_agg(jsonb_build_object('itemId',i.id,'equipmentId',i.equipment_id,
          'equipmentCode',e.code,'name',i.type_name,'dailyRate',i.daily_rate,'rentalAmount',i.rental_amount,
          'deposit',i.deposit_amount,'policyVersion',i.policy_version) order by e.code),'[]'::jsonb)
          from rental_item i join equipment e on e.id=i.equipment_id where i.order_id=o.id))
        from rental_order o where o.id=:o and a.order_id=o.id and a.revision=1 and a.snapshot is null
        """,p("o",order));}
    public void resolveLost(UUID item){s.update("update rental_item set resolved_at=clock_timestamp(),resolution_kind='LOST' where id=:i and returned_at is null and resolved_at is null",p("i",item));}
    public void closePendingChanges(UUID order,String reason){s.update("update rental_change set status='CANCELLED',decided_by='SYSTEM',decision_note=:reason,decided_at=clock_timestamp() where order_id=:o and status='PENDING'",p("o",order,"reason",reason));}
    public List<Map<String,Object>> attention(){return s.rows("""
        with situations as (
          select o.id order_id,o.code,o.status,o.starts_at,o.ends_at,c.name customer_name,
            i.id item_id,i.handed_at,e.code equipment_code,e.state equipment_state,
            exists(select 1 from rental_incident x where x.item_id=i.id and x.kind='LOST' and x.status='PENDING') lost_pending,
            (select prev.code from rental_item j join rental_order prev on prev.id=j.order_id
             where j.equipment_id=i.equipment_id and j.order_id<>o.id and j.handed_at is not null
             and j.returned_at is null and j.resolved_at is null and prev.ends_at<=clock_timestamp()
             order by prev.ends_at limit 1) previous_late,
            (select prev.code from rental_item j join rental_order prev on prev.id=j.order_id
             join rental_incident x on x.item_id=j.id and x.kind='LOST' and x.status='PENDING'
             where j.equipment_id=i.equipment_id and j.order_id<>o.id and j.returned_at is null
             and j.resolved_at is null order by prev.ends_at limit 1) previous_incident
          from rental_order o join customer c on c.id=o.customer_id
            join rental_item i on i.order_id=o.id join equipment e on e.id=i.equipment_id
          where o.status in ('HELD','CONFIRMED','PREPARING','RENTED','PARTIAL_RETURN')
            and (o.status<>'HELD' or o.expires_at>clock_timestamp())
            and i.returned_at is null and i.resolved_at is null
        )
        select order_id,code,status,starts_at,ends_at,customer_name,item_id,equipment_code,equipment_state,
          coalesce(previous_late,previous_incident) affected_by,
          case when lost_pending then 'LOST_PENDING'
               when handed_at is not null and ends_at<=clock_timestamp() then 'OVERDUE'
               when equipment_state in ('MAINTENANCE','SUSPENDED','DISPOSED','AWAIT_ACCEPTANCE') then 'UNSAFE'
               when previous_late is not null then 'LATE_PREVIOUS' else 'PREVIOUS_INCIDENT' end risk
        from situations where lost_pending or (handed_at is not null and ends_at<=clock_timestamp())
          or (handed_at is null and (equipment_state in ('MAINTENANCE','SUSPENDED','DISPOSED','AWAIT_ACCEPTANCE')
             or previous_late is not null or previous_incident is not null))
        order by ends_at,equipment_code limit 300
        """,Map.of());}
    public List<Map<String,Object>> due(int hours){return s.rows("""
        select o.id,o.code,o.ends_at,case when o.ends_at<=clock_timestamp() then 'OVERDUE_NOTICE' else 'DUE_REMINDER' end event_type
        from rental_order o where status in ('RENTED','PARTIAL_RETURN')
          and ends_at<=clock_timestamp()+(:hours * interval '1 hour')
          and exists(select 1 from rental_item i where i.order_id=o.id and handed_at is not null and returned_at is null and resolved_at is null)
          and not exists(select 1 from notification n where n.dedupe_key=concat(
            case when o.ends_at<=clock_timestamp() then 'OVERDUE_NOTICE' else 'DUE_REMINDER' end,
            ':',o.id,':',cast(floor(extract(epoch from o.ends_at)*1000) as bigint)))
        order by ends_at limit 300
        """,p("hours",hours));}
    public List<Map<String,Object>> notices(UUID order,boolean customer){return s.rows("select id,event_type,payload,status,recipient_scope,created_at from notification where order_id=:o"+(customer?" and recipient_scope='CUSTOMER'":"")+" order by created_at desc limit 100",p("o",order));}
}
