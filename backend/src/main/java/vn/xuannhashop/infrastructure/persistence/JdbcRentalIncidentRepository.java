package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.rental.application.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcRentalIncidentRepository implements RentalIncidentRepository {
    private final Sql s;public JdbcRentalIncidentRepository(Sql s){this.s=s;}
    public List<Map<String,Object>> list(UUID order){return s.rows("select x.*,i.type_name,e.code equipment_code from rental_incident x join rental_item i on i.id=x.item_id join equipment e on e.id=i.equipment_id where x.order_id=:o order by x.created_at desc",p("o",order));}
    public Map<String,Object> get(UUID id,boolean lock){return s.one("select * from rental_incident where id=:id"+(lock?" for update":""),p("id",id));}
    public void create(UUID id,UUID order,RentalIncidentApplication.Report report,String actor){s.update("insert into rental_incident(id,order_id,item_id,kind,amount,description,customer_name,agreement_basis,requested_by) values(:id,:o,:i,:k,:a,:d,:c,:b,:actor)",p("id",id,"o",order,"i",report.itemId(),"k",report.kind(),"a",report.amount(),"d",report.description(),"c",report.customerName(),"b",report.agreementBasis(),"actor",actor));}
    public void decide(UUID id,boolean approved,String actor,String note){s.update("update rental_incident set status=:s,decided_by=:a,decision_note=:n,decided_at=clock_timestamp() where id=:id",p("id",id,"s",approved?"APPROVED":"REJECTED","a",actor,"n",note));}
}
