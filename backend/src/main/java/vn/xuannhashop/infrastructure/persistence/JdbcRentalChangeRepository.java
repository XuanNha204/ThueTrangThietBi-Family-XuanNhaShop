package vn.xuannhashop.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.rental.application.RentalChangeRepository;
import vn.xuannhashop.business.rental.domain.RentalChangeDraft;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

@Repository
public class JdbcRentalChangeRepository implements RentalChangeRepository {
    private final Sql s; private final ObjectMapper json;
    public JdbcRentalChangeRepository(Sql s,ObjectMapper json){this.s=s;this.json=json;}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    public void create(UUID id,RentalChangeDraft draft,String actor,String role,String accepted){
        s.update("insert into rental_change(id,order_id,kind,base_version,draft,requested_by,requester_role,customer_accepted_by) values(:id,:o,:k,:v,cast(:draft as jsonb),:actor,:role,:accepted)",
            p("id",id,"o",draft.orderId(),"k",draft.kind(),"v",draft.baseVersion(),"draft",encode(draft),"actor",actor,"role",role,"accepted",accepted));
    }
    public Proposal get(UUID id,boolean lock){
        var row=s.one("select *,draft::text draft_text from rental_change where id=:id"+(lock?" for update":""),p("id",id));
        if(row==null)return null;
        try{return new Proposal(id,(UUID)row.get("order_id"),(String)row.get("status"),(String)row.get("requested_by"),(String)row.get("requester_role"),(String)row.get("customer_accepted_by"),json.readValue((String)row.get("draft_text"),RentalChangeDraft.class));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    public void consent(UUID id,String customer,String recorder,String note){s.update("update rental_change set customer_accepted_by=:c,consent_recorded_by=:r,consent_note=:n where id=:id",p("id",id,"c",customer,"r",recorder,"n",note));}
    public void status(UUID id,String status,String actor,String note){s.update("update rental_change set status=:status,decided_by=:a,decision_note=:n,decided_at=clock_timestamp() where id=:id",p("id",id,"status",status,"a",actor,"n",note));}
    public Map<String,Object> history(UUID order){
        var changes=s.rows("select id,kind,base_version,draft::text draft_json,requested_by,requester_role,customer_accepted_by,consent_recorded_by,consent_note,status,decision_note,decided_by,created_at,decided_at from rental_change where order_id=:o order by created_at desc",p("o",order));
        var revisions=s.rows("select revision,terms,accepted_by,accepted_at,requested_by,staff_confirmed_by,customer_confirmed_by,consent_note,change_id,snapshot::text snapshot_json from agreement_revision where order_id=:o order by revision desc",p("o",order));
        return Map.of("changes",changes,"revisions",revisions);
    }
    public void apply(UUID id,RentalChangeDraft draft,String actor){
        for(var item:draft.items())s.update("update rental_item set equipment_id=:e,rental_amount=:r where id=:id and order_id=:o",p("id",item.itemId(),"o",draft.orderId(),"e",item.equipmentId(),"r",item.rentalAmount()));
        s.update("update rental_order set starts_at=:f,ends_at=:t,address=:a,rental_total=:r,version=version+1 where id=:o",p("o",draft.orderId(),"f",draft.startsAt(),"t",draft.endsAt(),"a",draft.address(),"r",draft.rentalTotal()));
        String terms="Thay đổi "+draft.kind()+": "+draft.reason()+". Giá theo đơn giá đã chấp nhận; tiền cọc tiếp tục được theo dõi riêng.";
        s.update("insert into agreement_revision(id,order_id,revision,terms,accepted_by,requested_by,staff_confirmed_by,customer_confirmed_by,consent_note,change_id,snapshot) select :id,c.order_id,(select coalesce(max(a.revision),0)+1 from agreement_revision a where a.order_id=c.order_id),:terms,:actor,c.requested_by,:actor,c.customer_accepted_by,c.consent_note,c.id,c.draft from rental_change c where c.id=:change",p("id",UUID.randomUUID(),"terms",terms,"actor",actor,"change",id));
        status(id,"APPLIED",actor,"Đã kiểm tra lại lịch và ghi thỏa thuận mới.");
    }
}
