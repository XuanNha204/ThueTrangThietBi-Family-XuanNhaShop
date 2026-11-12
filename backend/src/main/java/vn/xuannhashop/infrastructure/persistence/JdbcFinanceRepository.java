package vn.xuannhashop.infrastructure.persistence;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.finance.application.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcFinanceRepository implements FinanceRepository {
    private final Sql s;public JdbcFinanceRepository(Sql s){this.s=s;}
    public FinanceApplication.Balance balances(UUID order){var r=s.one("select coalesce(sum(case when kind='DEPOSIT_IN' then amount when kind='DEPOSIT_REFUND' then -amount else 0 end),0) deposit,coalesce(sum(case when kind='RENTAL_IN' then amount else 0 end),0) rent,coalesce(sum(case when kind='CHARGE_IN' then amount else 0 end),0) charges_paid,(select coalesce(sum(amount),0) from rental_charge where order_id=:o) charges_due from money_transaction where order_id=:o",p("o",order));return new FinanceApplication.Balance((BigDecimal)r.get("deposit"),(BigDecimal)r.get("rent"),(BigDecimal)r.get("charges_due"),(BigDecimal)r.get("charges_paid"));}
    public Map<String,Object> lockOrder(UUID o){return s.one("select * from rental_order where id=:o for update",p("o",o));}
    public Map<String,Object> lockApproval(UUID id){return s.one("select * from approval where id=:id and type='DEPOSIT_REFUND' for update",p("id",id));}
    public List<Map<String,Object>> ledger(){return s.rows("select m.*,o.code order_code from money_transaction m left join rental_order o on o.id=m.order_id order by m.created_at desc limit 300",Map.of());}
    public List<Map<String,Object>> approvals(){return s.rows("select a.*,o.code order_code from approval a join rental_order o on o.id=a.target_id where a.type='DEPOSIT_REFUND' order by a.created_at desc limit 200",Map.of());}
    public void record(UUID id,UUID order,UUID payroll,String kind,BigDecimal amount,String key,String actor,String note,UUID approval){s.ledgerLock();s.update("insert into money_transaction(id,order_id,payroll_id,kind,amount,operation_key,recorded_by,note,approval_id,created_at) values(:id,:order,:payroll,:kind,:amount,:key,:actor,:note,:approval,clock_timestamp())",p("id",id,"order",order,"payroll",payroll,"kind",kind,"amount",amount,"key",key,"actor",actor,"note",note,"approval",approval));}
    public void request(UUID id,UUID order,BigDecimal amount,String actor,String reason){s.update("insert into approval(id,type,target_id,amount,requester,reason) values(:id,'DEPOSIT_REFUND',:o,:a,:actor,:reason)",p("id",id,"o",order,"a",amount,"actor",actor,"reason",reason));}
    public BigDecimal pendingRefunds(UUID order){return (BigDecimal)s.one("select coalesce(sum(amount),0) n from approval where target_id=:o and type='DEPOSIT_REFUND' and status='PENDING'",p("o",order)).get("n");}
    public boolean inspected(UUID order){return s.one("""
        select i.id from rental_item i where i.order_id=:o and (
          (i.resolution_kind='LOST' and i.resolved_at is not null) or
          (i.returned_at is not null and (
            exists(select 1 from inspection p where p.equipment_id=i.equipment_id and p.created_at>=i.returned_at and p.passed) or
            exists(select 1 from rental_incident x where x.item_id=i.id and x.kind='DAMAGE' and x.status='APPROVED')
          ))
        ) is not true limit 1
        """,p("o",order))==null;}
    public void decide(UUID id,boolean approved,String actor){s.update("update approval set status=:s,approver=:actor,decided_at=now() where id=:id",p("id",id,"s",approved?"APPROVED":"REJECTED","actor",actor));}
    public void addCharge(UUID order,UUID incident,BigDecimal amount,String reason,String approver){s.update("insert into rental_charge(id,order_id,incident_id,amount,reason,approved_by) values(:id,:o,:x,:a,:r,:actor)",p("id",UUID.randomUUID(),"o",order,"x",incident,"a",amount,"r",reason,"actor",approver));}
}
