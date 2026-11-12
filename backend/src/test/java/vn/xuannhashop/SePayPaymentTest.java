package vn.xuannhashop;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.xuannhashop.business.finance.application.*;
import vn.xuannhashop.business.finance.domain.SePayTransfer;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.infrastructure.config.DevSeed;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.jobs-enabled=false","app.sepay.enabled=true","app.sepay.webhook-secret=payment-test-only-secret-abcdef-2026","app.sepay.channel=payment-test"})
class SePayPaymentTest {
    @Autowired SePayPaymentApplication payments;@Autowired SePayPaymentWorker worker;
    @Autowired SePayInboxWriter inbox;@Autowired FinanceApplication finance;
    @Autowired JdbcTemplate db;@Autowired ObjectMapper json;
    String key(){return UUID.randomUUID().toString();}
    <T>T as(String name,Supplier<T> action){var previous=SecurityContextHolder.getContext();var current=SecurityContextHolder.createEmptyContext();current.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(name,null,List.of()));SecurityContextHolder.setContext(current);try{return action.get();}finally{SecurityContextHolder.setContext(previous);}}
    void act(String name,Runnable action){as(name,()->{action.run();return null;});}
    @BeforeEach void setup(){db.update("update sepay_bank_configuration set gateway='MBBank',account_number='000123456',account_holder='JUNIT ONLY',enabled=true,automatic_posting=true");}
    @AfterEach void cleanup(){db.update("update sepay_bank_configuration set enabled=false,automatic_posting=false");}
    UUID order(){UUID id=UUID.randomUUID();Instant now=Instant.now();db.update("insert into rental_order(id,code,customer_id,status,starts_at,ends_at,expires_at,rental_total,deposit_required,address,terms,agreement_version,created_by) values(?,?,?,'HELD',?,?,?,100,200,'JUNIT','JUNIT','DEV-1','owner')",id,"PAY-"+key(),DevSeed.id("khach-an"),Timestamp.from(now.plusSeconds(3600)),Timestamp.from(now.plusSeconds(86400)),Timestamp.from(now.plusSeconds(1200)));return id;}
    UUID request(UUID o,String kind){return as("customer",()->payments.create(o,kind,key()));}
    Map<String,Object> request(UUID id){return db.queryForMap("select * from bank_payment_request where id=?",id);}
    UUID event(UUID request,int amount,String account,String direction,long tx,String content)throws Exception{
        return eventAt(request,amount,account,direction,tx,content,Instant.now());
    }
    UUID eventAt(UUID request,int amount,String account,String direction,long tx,String content,Instant when)throws Exception{
        var req=request(request);var raw=new LinkedHashMap<String,Object>();raw.put("id",tx);raw.put("gateway","MBBank");raw.put("accountNumber",account);raw.put("transactionDate",LocalDateTime.ofInstant(when,ZoneId.of("Asia/Ho_Chi_Minh")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));raw.put("transferType",direction);raw.put("transferAmount",amount);raw.put("code",null);raw.put("content",content==null?req.get("code"):content);raw.put("referenceCode","JUNIT-"+key());raw.put("subAccount","");byte[] bytes=json.writeValueAsBytes(raw);
        var t=new vn.xuannhashop.infrastructure.partners.SePayJsonReader(json).parse(bytes);inbox.store("payment-test",t,bytes);
        return db.queryForObject("select id from sepay_webhook_event where channel='payment-test' and transaction_id=? order by received_at desc limit 1",UUID.class,tx);
    }
    UUID event(UUID request,int amount)throws Exception{return event(request,amount,"000123456","in",Math.abs(UUID.randomUUID().getMostSignificantBits()>>>1),null);}
    String disposition(UUID id){return db.queryForObject("select reconciliation_reason from sepay_webhook_event where id=?",String.class,id);}
    int count(UUID o){return db.queryForObject("select count(*) from money_transaction where order_id=?",Integer.class,o);}

    @Test void exactDepositPostsOnceAndNeverConfirmsOrReallocatesOrder()throws Exception{
        UUID o=order(),p=request(o,"DEPOSIT_IN"),e=event(p,200);UUID first=worker.process(e,null,null,"SYSTEM");assertThat(worker.process(e,null,null,"SYSTEM")).isEqualTo(first);
        assertThat(count(o)).isEqualTo(1);assertThat(finance.balances(o).depositHeld()).isEqualByComparingTo("200");assertThat(finance.balances(o).rentalPaid()).isZero();
        assertThat(db.queryForObject("select status from rental_order where id=?",String.class,o)).isEqualTo("HELD");assertThat(db.queryForObject("select count(*) from reservation where order_id=?",Integer.class,o)).isZero();assertThat(disposition(e)).isEqualTo("EXACT_MATCH");
    }
    @Test void oneOpenRequestAndOperationRetryUseSameCode(){UUID o=order();String k=key();UUID a=as("customer",()->payments.create(o,"RENTAL_IN",k));assertThat(as("customer",()->payments.create(o,"RENTAL_IN",k))).isEqualTo(a);assertThat(request(o,"RENTAL_IN")).isEqualTo(a);assertThat(as("customer",()->payments.requests(o))).hasSize(1);}
    @Test void mbankMinuteTimestampInCreationMinutePostsRentExactlyOnce()throws Exception{
        UUID o=order(),p=request(o,"RENTAL_IN");Instant minute=Instant.now().minusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        db.update("update bank_payment_request set created_at=? where id=?",Timestamp.from(minute.plusSeconds(30).plusMillis(314)),p);
        UUID e=eventAt(p,100,"000123456","in",101101,null,minute);UUID money=worker.process(e,null,null,"SYSTEM");
        assertThat(money).isNotNull();assertThat(worker.process(e,null,null,"SYSTEM")).isEqualTo(money);assertThat(count(o)).isEqualTo(1);
        assertThat(finance.balances(o).rentalPaid()).isEqualByComparingTo("100");assertThat(finance.balances(o).depositHeld()).isZero();assertThat(disposition(e)).isEqualTo("EXACT_MATCH");
        assertThat(db.queryForObject("select status from rental_order where id=?",String.class,o)).isEqualTo("HELD");
    }
    @Test void minuteToleranceDoesNotAllowEarlierMinutePreciseEarlierTimeOrFutureTime()throws Exception{
        UUID o=order(),p=request(o,"RENTAL_IN");Instant minute=Instant.now().minusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        db.update("update bank_payment_request set created_at=? where id=?",Timestamp.from(minute.plusSeconds(30)),p);
        long tx=101102;for(Instant time:List.of(minute.minusSeconds(60),minute.plusSeconds(15),Instant.now().plusSeconds(600))){UUID e=eventAt(p,100,"000123456","in",tx++,null,time);assertThat(worker.process(e,null,null,"SYSTEM")).isNull();assertThat(disposition(e)).isEqualTo("INVALID_TIME");}
        assertThat(count(o)).isZero();
    }
    @Test void simultaneousDifferentTransactionsForOneRequestNeverCollectTwice()throws Exception{
        UUID o=order(),p=request(o,"DEPOSIT_IN");List<UUID> events=List.of(event(p,200),event(p,200),event(p,200));var pool=Executors.newFixedThreadPool(3);var start=new CountDownLatch(1);var futures=new ArrayList<Future<UUID>>();
        try{for(UUID e:events)futures.add(pool.submit(()->{start.await();return worker.process(e,null,null,"SYSTEM");}));start.countDown();int posted=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS)!=null)posted++;assertThat(posted).isEqualTo(1);}finally{pool.shutdownNow();}assertThat(count(o)).isEqualTo(1);
    }
    @Test void wrongAccountOutgoingSampleAndConflictingPayloadCannotPost()throws Exception{
        UUID o=order(),p=request(o,"RENTAL_IN");UUID wrong=event(p,100,"999999999","in",101001,null);worker.process(wrong,null,null,"SYSTEM");assertThat(disposition(wrong)).isEqualTo("WRONG_ACCOUNT");
        UUID out=event(p,100,"000123456","out",101002,null);worker.process(out,null,null,"SYSTEM");assertThat(disposition(out)).isEqualTo("OUTGOING");
        UUID sample=event(p,100,"000123456","in",0,null);worker.process(sample,null,null,"SYSTEM");assertThat(disposition(sample)).isEqualTo("SAMPLE");
        UUID conflict=event(p,100,"000123456","in",101003,null);event(p,101,"000123456","in",101003,null);worker.process(conflict,null,null,"SYSTEM");assertThat(disposition(conflict)).isEqualTo("CONFLICT");assertThat(count(o)).isZero();
        assertThatThrownBy(()->as("accountant",()->payments.reconcile(wrong,p,"Đã xem sao kê",key()))).isInstanceOf(DomainException.class);
    }
    @Test void partialPaymentNeedsExplicitReviewThenNewRequestUsesRemainingDue()throws Exception{
        UUID o=order(),p=request(o,"DEPOSIT_IN"),e=event(p,150);worker.process(e,null,null,"SYSTEM");assertThat(disposition(e)).isEqualTo("AMOUNT_MISMATCH");assertThat(count(o)).isZero();String k=key();
        UUID m=as("accountant",()->payments.reconcile(e,p,"Khách chuyển thiếu, đã kiểm tra sao kê",k));assertThat(as("accountant",()->payments.reconcile(e,p,"Khách chuyển thiếu, đã kiểm tra sao kê",k))).isEqualTo(m);
        assertThat(finance.balances(o).depositHeld()).isEqualByComparingTo("150");UUID next=request(o,"DEPOSIT_IN");assertThat((BigDecimal)request(next).get("amount")).isEqualByComparingTo("50");assertThat(count(o)).isEqualTo(1);
    }
    @Test void overpaymentIsNeverSilentlyCappedOrPosted()throws Exception{
        UUID o=order(),p=request(o,"DEPOSIT_IN"),e=event(p,201);worker.process(e,null,null,"SYSTEM");assertThat(disposition(e)).isEqualTo("AMOUNT_MISMATCH");assertThatThrownBy(()->as("owner",()->payments.reconcile(e,p,"Đã kiểm tra số tiền thừa",key()))).isInstanceOf(DomainException.class);assertThat(count(o)).isZero();
    }
    @Test void lateDepositIsReviewableButNeverResurrectsExpiredHold()throws Exception{
        UUID o=order(),p=request(o,"DEPOSIT_IN"),e=event(p,200);db.update("update rental_order set expires_at=now()-interval '1 second',status='EXPIRED' where id=?",o);worker.process(e,null,null,"SYSTEM");assertThat(disposition(e)).isEqualTo("ORDER_CLOSED");assertThat(count(o)).isZero();
        as("accountant",()->payments.reconcile(e,p,"Cọc đến muộn, giữ để hoàn theo phê duyệt",key()));assertThat(finance.balances(o).depositHeld()).isEqualByComparingTo("200");assertThat(db.queryForObject("select status from rental_order where id=?",String.class,o)).isEqualTo("EXPIRED");
    }
    @Test void expiredPaymentRequestAndClosedOrderRentNeverAutoPost()throws Exception{
        UUID o=order(),p=request(o,"RENTAL_IN"),e=event(p,100);db.update("update bank_payment_request set created_at=now()-interval '1 hour',expires_at=now()-interval '1 minute' where id=?",p);worker.process(e,null,null,"SYSTEM");assertThat(disposition(e)).isEqualTo("LATE_PAYMENT");
        db.update("update rental_order set status='CANCELLED' where id=?",o);assertThatThrownBy(()->as("owner",()->payments.reconcile(e,p,"Đơn đã hủy",key()))).isInstanceOf(DomainException.class);assertThat(count(o)).isZero();
    }
    @Test void unknownMemoCanBeMatchedWithEvidenceAndIgnoreIsReversible()throws Exception{
        UUID o=order(),p=request(o,"RENTAL_IN"),e=event(p,100,"000123456","in",101004,"Khách quên mã");worker.process(e,null,null,"SYSTEM");assertThat(disposition(e)).isEqualTo("UNKNOWN_CODE");
        act("accountant",()->payments.ignore(e,"Chưa xác định khách",key()));assertThat(worker.process(e,null,null,"SYSTEM")).isNull();act("owner",()->payments.retry(e,key()));
        as("accountant",()->payments.reconcile(e,p,"Khách xác nhận số tham chiếu trên sao kê",key()));assertThat(count(o)).isEqualTo(1);assertThatThrownBy(()->act("owner",()->payments.ignore(e,"Không được xóa",key()))).isInstanceOf(DomainException.class);
    }
    @Test void automaticPostingCanBeDisabledAndManualReceiptRaceCannotOvercollect()throws Exception{
        UUID o=order(),p=request(o,"RENTAL_IN"),e=event(p,100);db.update("update sepay_bank_configuration set automatic_posting=false");worker.process(e,null,null,"SYSTEM");assertThat(disposition(e)).isEqualTo("AUTO_DISABLED");
        as("accountant",()->finance.receive(o,"RENTAL_IN",new BigDecimal("100"),"Tiền mặt đã thu",key()));assertThatThrownBy(()->as("accountant",()->payments.reconcile(e,p,"Đối chiếu sau khi đã thu khác",key()))).isInstanceOf(DomainException.class);assertThat(count(o)).isEqualTo(1);
    }
    @Test void injectedLedgerFailureRollsBackRequestReceiptAndAuditThenRetryWorks()throws Exception{
        UUID o=order(),p=request(o,"DEPOSIT_IN"),e=event(p,200);int audit=db.queryForObject("select count(*) from audit_entry",Integer.class);
        db.execute("create function fail_sepay_junit() returns trigger language plpgsql as $$ begin if NEW.operation_key like 'sepay:%' then raise exception 'junit injected'; end if; return NEW; end $$");db.execute("create trigger fail_sepay_junit before insert on money_transaction for each row execute function fail_sepay_junit()");
        try{assertThatThrownBy(()->worker.process(e,null,null,"SYSTEM")).isInstanceOf(Exception.class);assertThat(count(o)).isZero();assertThat(request(p).get("status")).isEqualTo("OPEN");assertThat(disposition(e)).isEqualTo("PENDING");assertThat(db.queryForObject("select count(*) from audit_entry",Integer.class)).isEqualTo(audit);}finally{db.execute("drop trigger fail_sepay_junit on money_transaction");db.execute("drop function fail_sepay_junit()");}
        worker.process(e,null,null,"SYSTEM");assertThat(count(o)).isEqualTo(1);
    }
    @Test void configurationVersionAndRoleAndCustomerScopeAreEnforced(){
        UUID o=order();assertThatThrownBy(()->as("stock",()->payments.create(o,"RENTAL_IN",key()))).isInstanceOf(DomainException.class);
        UUID other=UUID.randomUUID();db.update("insert into customer(id,name,phone) values(?,'JUNIT OTHER','0900000000')",other);db.update("update rental_order set customer_id=? where id=?",other,o);assertThatThrownBy(()->as("customer",()->payments.requests(o))).isInstanceOf(DomainException.class);
        var current=as("owner",()->payments.configuration());var c=new SePayPaymentApplication.BankConfiguration(((Number)current.get("version")).longValue(),"MBBank","000123456","JUNIT ONLY",true,true);
        assertThatThrownBy(()->act("accountant",()->payments.configure(c,key()))).isInstanceOf(DomainException.class);act("owner",()->payments.configure(c,key()));assertThatThrownBy(()->act("owner",()->payments.configure(c,key()))).isInstanceOf(DomainException.class);
    }
    @Test void staleQrIsNotPayableAfterManualReceiptAndNewCodeUsesExactRemainder(){
        UUID o=order(),p=request(o,"DEPOSIT_IN");assertThat(as("customer",()->payments.requests(o)).get(0).get("payable")).isEqualTo(true);
        as("accountant",()->finance.receive(o,"DEPOSIT_IN",new BigDecimal("50"),"Cọc tiền mặt đã đối chiếu",key()));
        assertThat(as("customer",()->payments.requests(o)).get(0).get("payable")).isEqualTo(false);
        UUID next=request(o,"DEPOSIT_IN");assertThat(next).isNotEqualTo(p);assertThat((BigDecimal)request(next).get("amount")).isEqualByComparingTo("150");assertThat(request(p).get("status")).isEqualTo("CANCELLED");
        assertThatThrownBy(()->as("customer",()->payments.create(o,null,key()))).isInstanceOf(DomainException.class);
    }
}
