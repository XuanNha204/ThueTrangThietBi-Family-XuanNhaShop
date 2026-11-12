package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.shared.audit.ActivityRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcActivityRepository implements ActivityRepository {
    private final Sql sql;public JdbcActivityRepository(Sql sql){this.sql=sql;}
    public void append(String actor,String action,UUID target,String detail){sql.update("insert into audit_entry(id,actor,action,target_id,detail) values(:id,:actor,:action,:target,:detail)",p("id",UUID.randomUUID(),"actor",actor,"action",action,"target",target,"detail",detail));}
    public List<Map<String,Object>> recent(){return sql.rows("select * from audit_entry order by created_at desc limit 200",Map.of());}
    public void notifyOrder(UUID order,String event,String payload){sql.update("insert into notification(id,order_id,event_type,payload) values(:id,:order,:event,:payload)",p("id",UUID.randomUUID(),"order",order,"event",event,"payload",payload));}
    public boolean notifyOnce(UUID order,String event,String payload,String key,String scope){return sql.update("insert into notification(id,order_id,event_type,payload,dedupe_key,recipient_scope) values(:id,:order,:event,:payload,:key,:scope) on conflict(dedupe_key) do nothing",p("id",UUID.randomUUID(),"order",order,"event",event,"payload",payload,"key",key,"scope",scope))==1;}
}
