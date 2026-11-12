package vn.xuannhashop.infrastructure.persistence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.payroll.application.PayrollRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcPayrollRepository implements PayrollRepository {
    private final Sql s;public JdbcPayrollRepository(Sql s){this.s=s;}
    public List<Map<String,Object>> staff(){return s.rows("select * from staff order by name",Map.of());}
    public List<Map<String,Object>> work(){return s.rows("select w.*,s.name staff_name from work_record w join staff s on s.id=w.staff_id order by work_date desc limit 300",Map.of());}
    public List<Map<String,Object>> payroll(){return s.rows("select p.*,s.name staff_name from payroll p join staff s on s.id=p.staff_id order by period desc,p.created_at desc limit 200",Map.of());}
    public Map<String,Object> lockStaff(UUID id){return s.one("select * from staff where id=:id and active for update",p("id",id));}
    public Map<String,Object> lockWork(UUID id){return s.one("select * from work_record where id=:id for update",p("id",id));}
    public Map<String,Object> lockPayroll(UUID id){return s.one("select * from payroll where id=:id for update",p("id",id));}
    public boolean periodLocked(UUID staff,String period){return s.one("select id from payroll where staff_id=:s and period=:p",p("s",staff,"p",period))!=null;}
    public BigDecimal approvedHours(UUID staff,String period){return (BigDecimal)s.one("select coalesce(sum(hours),0) h from work_record where staff_id=:s and to_char(work_date,'YYYY-MM')=:p and approved_by is not null",p("s",staff,"p",period)).get("h");}
    public void work(UUID id,UUID staff,LocalDate date,BigDecimal hours,String actor){s.update("insert into work_record values(:id,:s,:d,:h,:a,null)",p("id",id,"s",staff,"d",date,"h",hours,"a",actor));}
    public void approveWork(UUID id,String actor){s.update("update work_record set approved_by=:a where id=:id",p("id",id,"a",actor));}
    public void draft(UUID id,UUID staff,String period,BigDecimal hours,BigDecimal rate,BigDecimal amount,String actor){s.update("insert into payroll(id,staff_id,period,approved_hours,hourly_rate,amount,policy_version,status,prepared_by) values(:id,:s,:p,:h,:r,:a,'DEV-HOURLY-1','DRAFT',:actor)",p("id",id,"s",staff,"p",period,"h",hours,"r",rate,"a",amount,"actor",actor));s.update("insert into payroll_line(id,payroll_id,kind,amount,basis) values(:id,:payroll,'BASIC',:amount,:basis)",p("id",UUID.randomUUID(),"payroll",id,"amount",amount.subtract(adjustmentTotal(staff,period)),"basis",hours+" giờ × "+rate+"; DEV-HOURLY-1"));for(var a:s.rows("select * from payroll_adjustment where staff_id=:s and period=:p and status='APPROVED' order by id",p("s",staff,"p",period)))s.update("insert into payroll_line(id,payroll_id,kind,amount,basis,adjustment_id) values(:id,:payroll,:k,:a,:b,:adjustment)",p("id",UUID.randomUUID(),"payroll",id,"k",a.get("kind"),"a",Set.of("ADVANCE","DEDUCTION").contains(a.get("kind"))?((BigDecimal)a.get("amount")).negate():a.get("amount"),"b",a.get("basis"),"adjustment",a.get("id")));}
    public void status(UUID id,String status,String actor){s.update("update payroll set status=:s,approved_by=case when :s='APPROVED' then :a else approved_by end where id=:id",p("id",id,"s",status,"a",actor));}
    public boolean pendingAdjustments(UUID staff,String period){return s.one("select 1 from payroll_adjustment where staff_id=:s and period=:p and status='PENDING'",p("s",staff,"p",period))!=null;}
    public BigDecimal adjustmentTotal(UUID staff,String period){return (BigDecimal)s.one("select coalesce(sum(case when kind in ('DEDUCTION','ADVANCE') then -amount else amount end),0) n from payroll_adjustment where staff_id=:s and period=:p and status='APPROVED'",p("s",staff,"p",period)).get("n");}
}
