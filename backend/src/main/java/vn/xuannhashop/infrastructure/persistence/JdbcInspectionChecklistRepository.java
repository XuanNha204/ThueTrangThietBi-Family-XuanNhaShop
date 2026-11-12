package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.maintenance.application.InspectionChecklistRepository;
import vn.xuannhashop.business.maintenance.application.InspectionChecklistApplication.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

@Repository
public class JdbcInspectionChecklistRepository implements InspectionChecklistRepository {
    private final Sql s;public JdbcInspectionChecklistRepository(Sql s){this.s=s;}
    public Template current(UUID type,boolean lock){
        if(lock)s.update("insert into inspection_checklist_current(type_id) values(:id) on conflict do nothing",p("id",type));
        var row=s.one("select version from inspection_checklist_current where type_id=:id"+(lock?" for update":""),p("id",type));
        long version=row==null?0:((Number)row.get("version")).longValue();
        return new Template(type,version,s.rows("select c.* from inspection_checklist_criterion c join inspection_checklist_revision r on r.id=c.revision_id where r.type_id=:id and r.version=:v order by c.position",p("id",type,"v",version)).stream().map(c->new Criterion((String)c.get("code"),(String)c.get("label"),(Boolean)c.get("blocking"))).toList());
    }
    public void publish(UUID id,UUID type,long version,List<Criterion> checks,String reason,String actor){
        s.update("insert into inspection_checklist_revision(id,type_id,version,reason,created_by) values(:id,:type,:v,:reason,:actor)",p("id",id,"type",type,"v",version,"reason",reason,"actor",actor));
        for(int i=0;i<checks.size();i++){var c=checks.get(i);s.update("insert into inspection_checklist_criterion values(:id,:code,:label,:blocking,:pos)",p("id",id,"code",c.code(),"label",c.label(),"blocking",c.blocking(),"pos",i));}
        s.update("update inspection_checklist_current set version=:v where type_id=:type",p("v",version,"type",type));
    }
    public List<Map<String,Object>> history(UUID type){var rows=s.rows("select * from inspection_checklist_revision where type_id=:type order by version desc limit 50",p("type",type));for(var row:rows)row.put("checks",s.rows("select code,label,blocking from inspection_checklist_criterion where revision_id=:id order by position",p("id",row.get("id"))));return rows;}
    public void results(UUID inspection,long version,List<Result> results){
        s.update("update inspection set type_checklist_version=:v,results_recorded=true where id=:id",p("id",inspection,"v",version));
        for(int i=0;i<results.size();i++){var c=results.get(i);s.update("insert into inspection_check_result values(:id,:code,:label,:blocking,:passed,:note,:pos)",p("id",inspection,"code",c.code(),"label",c.label(),"blocking",c.blocking(),"passed",c.passed(),"note",c.note(),"pos",i));}
    }
}
