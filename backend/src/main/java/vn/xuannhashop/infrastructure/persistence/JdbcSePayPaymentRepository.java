package vn.xuannhashop.infrastructure.persistence;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.finance.application.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

@Repository
public class JdbcSePayPaymentRepository implements SePayPaymentRepository {
    private final Sql s;
    public JdbcSePayPaymentRepository(Sql s){this.s=s;}
    public Map<String,Object> configuration(boolean lock){return s.one("select * from sepay_bank_configuration where id=1"+(lock?" for update":""),Map.of());}
    public void configure(SePayPaymentApplication.BankConfiguration c,String actor){s.update("update sepay_bank_configuration set version=version+1,gateway=:g,account_number=:a,account_holder=:h,enabled=:e,automatic_posting=:auto,updated_by=:actor,updated_at=clock_timestamp() where id=1",p("g",c.gateway(),"a",c.accountNumber(),"h",c.accountHolder(),"e",c.enabled(),"auto",c.automaticPosting(),"actor",actor));}
    public List<Map<String,Object>> requests(UUID order){return s.rows("select id,order_id,code,kind,amount::text amount,gateway,account_number,account_holder,status,created_at,expires_at,money_id from bank_payment_request where order_id=:o order by created_at desc limit 100",p("o",order));}
    public Map<String,Object> request(UUID id,boolean lock){return s.one("select * from bank_payment_request where id=:id"+(lock?" for update":""),p("id",id));}
    public Map<String,Object> requestByCode(String code){return s.one("select * from bank_payment_request where code=:code",p("code",code));}
    public Map<String,Object> openRequest(UUID order,String kind){return s.one("select * from bank_payment_request where order_id=:o and kind=:k and status='OPEN'",p("o",order,"k",kind));}
    public void expireRequests(UUID order,Instant now){s.update("update bank_payment_request set status='EXPIRED' where order_id=:o and status='OPEN' and expires_at<=:now",p("o",order,"now",now));}
    public void insert(Map<String,Object> r){var values=new HashMap<String,Object>();r.forEach((k,v)->values.put(k,v instanceof Instant t?java.sql.Timestamp.from(t):v));s.update("insert into bank_payment_request(id,order_id,code,kind,amount,gateway,account_number,account_holder,created_by,created_at,expires_at) values(:id,:order_id,:code,:kind,:amount,:gateway,:account_number,:account_holder,:created_by,:created_at,:expires_at)",values);}
    public void cancel(UUID id){s.update("update bank_payment_request set status='CANCELLED' where id=:id and status='OPEN'",p("id",id));}
    public void paid(UUID id,UUID money){s.update("update bank_payment_request set status='PAID',money_id=:m where id=:id",p("id",id,"m",money));}
    public Map<String,Object> event(UUID id){return s.one("select * from sepay_webhook_event where id=:id for update",p("id",id));}
    public List<UUID> pending(){return s.rows("select id from sepay_webhook_event where reconciliation_status='PENDING' order by received_at limit 40",Map.of()).stream().map(m->(UUID)m.get("id")).toList();}
    public void disposition(UUID id,String status,String reason,UUID request,UUID money,String actor,String note){s.update("update sepay_webhook_event set reconciliation_status=:s,reconciliation_reason=:r,payment_request_id=:p,money_id=:m,reconciled_by=:actor,reconciled_at=clock_timestamp(),resolution_note=:note where id=:id",p("id",id,"s",status,"r",reason,"p",request,"m",money,"actor",actor,"note",note));}
}
