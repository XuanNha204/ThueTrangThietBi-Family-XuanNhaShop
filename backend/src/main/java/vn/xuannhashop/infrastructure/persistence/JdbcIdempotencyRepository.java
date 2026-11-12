package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.shared.IdempotencyRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {
    private final Sql sql;public JdbcIdempotencyRepository(Sql sql){this.sql=sql;}
    public void lock(String key){sql.rows("select pg_advisory_xact_lock(hashtextextended(:key,0))",p("key",key));}
    public Receipt find(String key){var r=sql.one("select * from command_receipt where operation_key=:key",p("key",key));return r==null?null:new Receipt((String)r.get("actor"),(String)r.get("fingerprint"),(UUID)r.get("result_id"));}
    public void save(String key,String actor,String hash,UUID id){sql.update("insert into command_receipt values(:key,:actor,:hash,:id,now())",p("key",key,"actor",actor,"hash",hash,"id",id));}
}
