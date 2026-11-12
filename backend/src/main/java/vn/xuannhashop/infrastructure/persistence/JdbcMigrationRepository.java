package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.migration.application.MigrationRepository;
import vn.xuannhashop.business.migration.domain.ImportRow;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcMigrationRepository implements MigrationRepository {
    private final Sql s;public JdbcMigrationRepository(Sql s){this.s=s;}
    public List<Map<String,Object>> list(String actor){return s.rows("select * from import_batch"+(actor==null?"":" where prepared_by=:a")+" order by created_at desc limit 100",p("a",actor));}
    public Map<String,Object> batch(UUID id,boolean lock){return s.one("select * from import_batch where id=:id"+(lock?" for update":""),p("id",id));}
    public List<Map<String,Object>> rows(UUID id){return s.rows("select * from import_row where batch_id=:id order by row_no",p("id",id));}
    public void stage(UUID id,String source,String filename,String actor,List<ImportRow> rows){s.update("insert into import_batch(id,source_key,filename,status,total_rows,valid_rows,prepared_by) values(:id,:s,:f,'STAGED',:n,0,:a)",p("id",id,"s",source,"f",filename,"n",rows.size(),"a",actor));for(var row:rows)s.update("insert into import_row(id,batch_id,row_no,source_id,name,phone,address,errors) values(:id,:b,:no,:s,:n,:p,:a,:e)",p("id",row.id(),"b",id,"no",row.rowNo(),"s",row.sourceId(),"n",row.name(),"p",row.phone(),"a",row.address(),"e",row.errors()));}
    public void summarize(UUID id,int total,int valid){s.update("update import_batch set valid_rows=:v,status=:s where id=:id",p("id",id,"v",valid,"s",total==valid?"STAGED":"INVALID"));}
    public boolean sourceExists(String source){return s.one("select source_id from import_source_link where source_id=:s",p("s",source))!=null;}
    public boolean phoneExists(String phone){return s.one("select id from customer where phone=:p limit 1",p("p",phone))!=null;}
    public void lockSources(List<String> sources){for(var source:sources)s.rows("select pg_advisory_xact_lock(hashtextextended(:s,0))",p("s","import-source:"+source));}
    public void link(UUID row,String source,UUID customer){s.update("insert into import_source_link values(:s,:c)",p("s",source,"c",customer));s.update("update import_row set customer_id=:c where id=:id",p("id",row,"c",customer));}
    public void committed(UUID id,String actor){s.update("update import_batch set status='COMMITTED',approved_by=:a where id=:id",p("id",id,"a",actor));}
}
