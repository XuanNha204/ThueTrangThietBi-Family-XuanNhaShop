package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.maintenance.application.*;
import vn.xuannhashop.business.maintenance.application.WarrantyApplication.Coverage;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository public class JdbcWarrantyRepository implements WarrantyRepository {
    private final Sql s;public JdbcWarrantyRepository(Sql s){this.s=s;}
    public Map<String,Object> overview(){return Map.of("coverage",s.rows("select c.*,e.code from warranty_coverage c join equipment e on e.id=c.equipment_id order by ends_on",Map.of()),"cases",s.rows("select c.*,e.code from warranty_case c join equipment e on e.id=c.equipment_id order by opened_at desc limit 300",Map.of()));}
    public Map<String,Object> coverage(UUID id){return s.one("select * from warranty_coverage where equipment_id=:id",p("id",id));}
    public void coverage(Coverage c,String actor){s.update("insert into warranty_coverage(equipment_id,version,provider,document_ref,terms,starts_on,ends_on,updated_by) values(:id,:v,:p,:d,:t,:f,:u,:a) on conflict(equipment_id) do update set version=excluded.version,provider=excluded.provider,document_ref=excluded.document_ref,terms=excluded.terms,starts_on=excluded.starts_on,ends_on=excluded.ends_on,updated_by=excluded.updated_by,updated_at=clock_timestamp()",p("id",c.equipmentId(),"v",c.expectedVersion()+1,"p",c.provider(),"d",c.documentRef(),"t",c.terms(),"f",c.startsOn(),"u",c.endsOn(),"a",actor));}
    public void open(UUID id,UUID e,Map<String,Object> c,String problem,String ref,String actor){s.update("insert into warranty_case(id,equipment_id,coverage_version,provider,document_ref,terms,ends_on,problem,partner_reference,opened_by) values(:id,:e,:v,:p,:d,:t,:u,:problem,:ref,:a)",p("id",id,"e",e,"v",c.get("version"),"p",c.get("provider"),"d",c.get("document_ref"),"t",c.get("terms"),"u",c.get("ends_on"),"problem",problem,"ref",ref,"a",actor));}
    public Map<String,Object> claim(UUID id){return s.one("select * from warranty_case where id=:id for update",p("id",id));}
    public void close(UUID id,String outcome,String result,String actor){s.update("update warranty_case set status='CLOSED',outcome=:o,result=:r,closed_by=:a,closed_at=clock_timestamp() where id=:id",p("id",id,"o",outcome,"r",result,"a",actor));}
}
