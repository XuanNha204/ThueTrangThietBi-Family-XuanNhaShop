package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.finance.application.AccountingRepository;
import vn.xuannhashop.business.finance.application.AccountingApplication.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository public class JdbcAccountingRepository implements AccountingRepository {
    private final Sql s;private final JdbcFinanceRepository finance;
    public JdbcAccountingRepository(Sql s,JdbcFinanceRepository finance){this.s=s;this.finance=finance;}
    public Map<String,Object> overview(){return Map.of("equipment",s.rows("select e.id,e.code,t.name from equipment e join equipment_type t on t.id=e.type_id order by e.code",Map.of()),"suppliers",s.rows("select * from supplier order by name",Map.of()),"expenses",s.rows("select e.*,v.name supplier_name,coalesce((select sum(m.amount) from money_transaction m where m.expense_id=e.id),0) paid from expense e left join supplier v on v.id=e.supplier_id order by e.created_at desc limit 500",Map.of()),"payables",s.rows("select v.id,v.name,coalesce(sum(e.amount),0) total,coalesce(sum((select coalesce(sum(m.amount),0) from money_transaction m where m.expense_id=e.id)),0) paid from supplier v left join expense e on e.supplier_id=v.id and e.status='APPROVED' group by v.id order by v.name",Map.of()),"reconciliations",s.rows("select * from shift_reconciliation order by created_at desc limit 100",Map.of()));}
    public void supplier(UUID id,String name,String contact,String document,String actor){s.update("insert into supplier(id,name,contact,document_ref,created_by) values(:id,:n,:c,:d,:a)",p("id",id,"n",name,"c",contact,"d",document,"a",actor));}
    public boolean supplierExists(UUID id){return s.one("select id from supplier where id=:id",p("id",id))!=null;}
    public boolean equipmentExists(UUID id){return s.one("select id from equipment where id=:id",p("id",id))!=null;}
    public Map<String,Object> purchase(UUID id){return s.one("select r.*,r.unit_cost*(select count(*) from purchase_receipt_line l where l.receipt_id=r.id) total from purchase_receipt r where id=:id for share",p("id",id));}
    public void expense(UUID id,Expense c,String actor){s.update("insert into expense(id,supplier_id,purchase_receipt_id,equipment_id,category,incurred_on,due_on,amount,document_ref,description,requested_by) values(:id,:s,:r,:e,:c,:i,:d,:a,:doc,:description,:actor)",p("id",id,"s",c.supplierId(),"r",c.purchaseReceiptId(),"e",c.equipmentId(),"c",c.category(),"i",c.incurredOn(),"d",c.dueOn(),"a",c.amount(),"doc",c.documentRef(),"description",c.description(),"actor",actor));}
    public Map<String,Object> expense(UUID id){return s.one("select * from expense where id=:id for update",p("id",id));}
    public void decideExpense(UUID id,boolean accepted,String actor,String note){s.update("update expense set status=:s,decided_by=:a,decision_note=:n,decided_at=clock_timestamp() where id=:id",p("id",id,"s",accepted?"APPROVED":"REJECTED","a",actor,"n",note));}
    public BigDecimal paid(UUID id){return (BigDecimal)s.one("select coalesce(sum(amount),0) n from money_transaction where expense_id=:id",p("id",id)).get("n");}
    public void pay(UUID id,UUID expense,BigDecimal amount,String evidence,String actor,String key){finance.record(id,null,null,"EXPENSE_OUT",amount,key,actor,evidence,null);s.update("update money_transaction set expense_id=:e where id=:id",p("id",id,"e",expense));}
    public void ledgerLock(){s.ledgerLock();}
    public Map<String,Object> ledgerSnapshot(Instant from,Instant to){return s.one("select coalesce(sum(case when kind in ('DEPOSIT_IN','RENTAL_IN','CHARGE_IN','RECOVERY_IN') then amount else -amount end),0) movement,coalesce(md5(string_agg(id::text||':'||kind||':'||amount::text,',' order by id)),'EMPTY') fingerprint from money_transaction where created_at>=:f and created_at<:t",p("f",from,"t",to));}
    public boolean overlaps(Instant from,Instant to){return s.one("select 1 from shift_reconciliation where status='APPROVED' and starts_at<:t and ends_at>:f",p("f",from,"t",to))!=null;}
    public void reconcile(UUID id,Reconciliation c,Map<String,Object> l,String actor){BigDecimal expected=c.openingBalance().add((BigDecimal)l.get("movement"));s.update("insert into shift_reconciliation(id,starts_at,ends_at,opening_balance,counted_balance,expected_balance,difference,ledger_fingerprint,reason,requested_by) values(:id,:f,:t,:o,:c,:e,:d,:fingerprint,:r,:a)",p("id",id,"f",c.startsAt(),"t",c.endsAt(),"o",c.openingBalance(),"c",c.countedBalance(),"e",expected,"d",c.countedBalance().subtract(expected),"fingerprint",l.get("fingerprint"),"r",c.reason(),"a",actor));}
    public Map<String,Object> reconciliation(UUID id){return s.one("select * from shift_reconciliation where id=:id for update",p("id",id));}
    public void decideReconciliation(UUID id,boolean accepted,String actor,String note){s.update("update shift_reconciliation set status=:s,decided_by=:a,decision_note=:n,decided_at=clock_timestamp() where id=:id",p("id",id,"s",accepted?"APPROVED":"REJECTED","a",actor,"n",note));}
}
