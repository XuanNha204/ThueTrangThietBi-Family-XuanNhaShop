package vn.xuannhashop.infrastructure.persistence;
import java.util.*;import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.payroll.application.*;import vn.xuannhashop.business.payroll.application.PayrollExtrasApplication.Adjustment;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository public class JdbcPayrollExtrasRepository implements PayrollExtrasRepository {
    private final Sql s;public JdbcPayrollExtrasRepository(Sql s){this.s=s;}
    public Map<String,Object> overview(){return Map.of("adjustments",s.rows("select a.*,st.name staff_name from payroll_adjustment a join staff st on st.id=a.staff_id order by a.created_at desc limit 500",Map.of()),"questions",s.rows("select q.*,p.period,st.name staff_name from payroll_question q join payroll p on p.id=q.payroll_id join staff st on st.id=p.staff_id order by q.created_at desc limit 300",Map.of()));}
    public Map<String,Object> mine(UUID account){var pays=s.rows("select p.*,st.name staff_name from payroll p join staff st on st.id=p.staff_id where st.user_id=:id and p.status in ('APPROVED','PAID') order by p.period desc limit 120",p("id",account));for(var pay:pays)pay.put("lines",s.rows("select kind,amount,basis from payroll_line where payroll_id=:id order by kind,id",p("id",pay.get("id"))));return Map.of("payroll",pays,"questions",s.rows("select q.*,p.period from payroll_question q join payroll p on p.id=q.payroll_id join staff st on st.id=p.staff_id where st.user_id=:id order by q.created_at desc limit 300",p("id",account)));}
    public boolean linkable(UUID account){return s.one("select id from user_account u where id=:id and enabled and role<>'CUSTOMER' and not exists(select 1 from staff st where st.user_id=u.id) for update",p("id",account))!=null;}
    public void link(UUID staff,UUID account){s.update("update staff set user_id=:a where id=:s",p("s",staff,"a",account));}
    public void adjustment(UUID id,Adjustment c,String actor){s.update("insert into payroll_adjustment(id,staff_id,period,kind,amount,basis,requested_by) values(:id,:s,:p,:k,:a,:b,:actor)",p("id",id,"s",c.staffId(),"p",c.period(),"k",c.kind(),"a",c.amount(),"b",c.basis(),"actor",actor));}
    public Map<String,Object> adjustment(UUID id){return s.one("select * from payroll_adjustment where id=:id for update",p("id",id));}
    public void decide(UUID id,boolean accepted,String actor,String note){s.update("update payroll_adjustment set status=:s,decided_by=:a,decision_note=:n,decided_at=clock_timestamp() where id=:id",p("id",id,"s",accepted?"APPROVED":"REJECTED","a",actor,"n",note));}
    public boolean owns(UUID account,UUID payroll){return s.one("select p.id from payroll p join staff st on st.id=p.staff_id where p.id=:p and st.user_id=:a and p.status in ('APPROVED','PAID')",p("p",payroll,"a",account))!=null;}
    public void question(UUID id,UUID payroll,String question,String actor){s.update("insert into payroll_question(id,payroll_id,requested_by,question) values(:id,:p,:a,:q)",p("id",id,"p",payroll,"a",actor,"q",question));}
    public Map<String,Object> question(UUID id){return s.one("select * from payroll_question where id=:id for update",p("id",id));}
    public void answer(UUID id,String answer,String actor){s.update("update payroll_question set status='RESOLVED',answer=:answer,answered_by=:a,answered_at=clock_timestamp() where id=:id",p("id",id,"answer",answer,"a",actor));}
}
