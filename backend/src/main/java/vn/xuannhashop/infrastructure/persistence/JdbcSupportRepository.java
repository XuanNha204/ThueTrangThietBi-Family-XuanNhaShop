package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.support.application.SupportRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcSupportRepository implements SupportRepository {
    private final Sql s;public JdbcSupportRepository(Sql s){this.s=s;}
    public List<Map<String,Object>> tickets(String user){return s.rows("select t.*,o.code order_code from support_ticket t left join rental_order o on o.id=t.order_id"+(user==null?"":" where t.created_by=:u")+" order by t.created_at desc limit 200",p("u",user));}
    public void create(UUID id,UUID order,String subject,String message,String actor){s.update("insert into support_ticket(id,order_id,subject,message,created_by) values(:id,:o,:s,:m,:a)",p("id",id,"o",order,"s",subject,"m",message,"a",actor));}
    public void resolve(UUID id,String resolution,String actor){s.update("update support_ticket set status='RESOLVED',resolution=:r,resolved_by=:a where id=:id",p("id",id,"r",resolution,"a",actor));}
    public Map<String,Object> ticket(UUID id){return s.one("select * from support_ticket where id=:id for update",p("id",id));}
    public List<Map<String,Object>> notifications(){return s.rows("select n.id,n.event_type,n.status,n.attempts,n.created_at,n.sent_at,n.last_error,o.code order_code from notification n left join rental_order o on o.id=n.order_id order by n.created_at desc limit 200",Map.of());}
    @Transactional public List<Map<String,Object>> claim(){return s.rows("with work as (select id from notification where attempts<5 and (status='PENDING' or (status='PROCESSING' and lease_until<clock_timestamp())) order by created_at limit 20 for update skip locked) update notification n set status='PROCESSING',attempts=attempts+1,lease_until=clock_timestamp()+interval '1 minute' from work where n.id=work.id returning n.*",Map.of());}
    public void sent(UUID id,int attempt){s.update("update notification set status='SENT',sent_at=now(),lease_until=null where id=:id and attempts=:a",p("id",id,"a",attempt));}
    public void failed(UUID id,int attempt){s.update("update notification set status=case when attempts>=5 then 'FAILED' else 'PROCESSING' end,last_error='Adapter dev không gửi được; kiểm tra kho nhật ký thông báo' where id=:id and attempts=:a",p("id",id,"a",attempt));}
}
