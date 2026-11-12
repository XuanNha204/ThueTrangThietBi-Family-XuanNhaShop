package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.delivery.application.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcDeliveryRepository implements DeliveryRepository {
    private final Sql s;public JdbcDeliveryRepository(Sql s){this.s=s;}
    public UUID record(UUID order,List<UUID> items,String kind,String accepted,String condition,String note,String actor){var id=UUID.randomUUID();s.update("insert into handover(id,order_id,kind,performed_by,accepted_by,note) values(:id,:o,:k,:a,:c,:n)",p("id",id,"o",order,"k",kind,"a",actor,"c",accepted,"n",note));for(var item:items)s.update("insert into handover_line values(:id,:h,:i,:c,:n)",p("id",UUID.randomUUID(),"h",id,"i",item,"c",condition,"n",note));return id;}
    public List<Map<String,Object>> list(UUID o){return s.rows("select h.*,(select count(*) from handover_line l where l.handover_id=h.id) item_count from handover h where order_id=:o order by created_at desc",p("o",o));}
    public void evidence(UUID id,UUID order,AttachmentRepository.Stored f,String actor){s.update("insert into evidence(id,order_id,original_name,file_key,content_type,size,checksum,uploaded_by) values(:id,:o,:n,:k,:t,:s,:c,:a)",p("id",id,"o",order,"n",f.originalName(),"k",f.key(),"t",f.contentType(),"s",f.size(),"c",f.checksum(),"a",actor));}
    public Map<String,Object> evidence(UUID id){return s.one("select * from evidence where id=:id",p("id",id));}
    public List<Map<String,Object>> evidenceFor(UUID o){return s.rows("select id,original_name,content_type,size,checksum,uploaded_by,created_at from evidence where order_id=:o order by created_at desc",p("o",o));}
}
