package vn.xuannhashop.infrastructure.persistence;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class Sql {
    private final NamedParameterJdbcTemplate jdbc;
    public Sql(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public static Map<String,Object> p(Object... a) { var m=new HashMap<String,Object>();for(int i=0;i<a.length;i+=2) { Object v=a[i+1];m.put((String)a[i],v instanceof Instant t?Timestamp.from(t):v); }return m; }
    public List<Map<String,Object>> rows(String q,Map<String,?> p) {
        return jdbc.query(q,p,(rs,n)->{var m=new LinkedHashMap<String,Object>();var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++) { var v=rs.getObject(i);if(v instanceof Timestamp t)v=t.toInstant();if(v instanceof java.sql.Date dt)v=dt.toLocalDate();m.put(meta.getColumnLabel(i),v); }return m;});
    }
    public Map<String,Object> one(String q,Map<String,?> p) { var r=rows(q,p);return r.isEmpty()?null:r.get(0); }
    public int update(String q,Map<String,?> p) { return jdbc.update(q,p); }
    public NamedParameterJdbcTemplate jdbc() { return jdbc; }
    public void ledgerLock(){rows("select pg_advisory_xact_lock(hashtextextended('money-ledger-posting',0))",Map.of());}
}
