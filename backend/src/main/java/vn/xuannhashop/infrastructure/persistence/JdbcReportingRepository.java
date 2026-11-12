package vn.xuannhashop.infrastructure.persistence;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.reporting.application.ReportingRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcReportingRepository implements ReportingRepository {
    private final Sql s;public JdbcReportingRepository(Sql s){this.s=s;}
    public Map<String,Object> operations(){return s.one("select (select count(*) from equipment) total_assets,(select count(*) from equipment where state='READY') ready_assets,(select count(*) from equipment where state='RENTED') rented_assets,(select count(*) from equipment where state in ('AWAIT_ACCEPTANCE','AWAIT_INSPECTION','MAINTENANCE','SUSPENDED')) attention_assets,(select count(*) from rental_order where status not in ('COMPLETED','CANCELLED','EXPIRED')) active_orders,(select count(*) from rental_order where status='HELD' and expires_at>clock_timestamp()) held_orders,(select count(*) from customer) customers,(select count(*) from support_ticket where status='OPEN') open_tickets,(select count(*) from rental_item i join rental_order o on o.id=i.order_id where i.handed_at is not null and i.returned_at is null and o.ends_at<clock_timestamp()) overdue_items",Map.of());}
    public Map<String,Object> finance(){return s.one("select coalesce(sum(case when kind='RENTAL_IN' then amount else 0 end),0) rental_received,coalesce(sum(case when kind='DEPOSIT_IN' then amount when kind='DEPOSIT_REFUND' then -amount else 0 end),0) deposits_held,coalesce(sum(case when kind='PAYROLL_OUT' then amount else 0 end),0) payroll_paid from money_transaction",Map.of());}
    public List<Map<String,Object>> trend(){return s.rows("select current_date-6+g.n as \"day\",count(o.id) as orders from generate_series(0,6) as g(n) left join rental_order o on (o.created_at at time zone 'Asia/Ho_Chi_Minh')::date=current_date-6+g.n group by g.n order by g.n",Map.of());}
    public List<Map<String,Object>> category(){return s.rows("select t.category,count(e.id) total,count(e.id) filter(where e.state='READY') ready,count(e.id) filter(where e.state='RENTED') rented from equipment_type t left join equipment e on e.type_id=t.id group by t.category order by total desc",Map.of());}
    public List<Map<String,Object>> ledger(LocalDate from,LocalDate until){return s.rows("select m.created_at,o.code order_code,m.kind,m.amount,m.recorded_by,m.note from money_transaction m left join rental_order o on o.id=m.order_id where (m.created_at at time zone 'Asia/Ho_Chi_Minh')::date between :f and :t order by m.created_at limit 10001",p("f",from,"t",until));}
    public void export(String actor,String scope){s.update("insert into report_job values(:id,:a,'FINANCE_CSV',:s,now())",p("id",UUID.randomUUID(),"a",actor,"s",scope));}
}
