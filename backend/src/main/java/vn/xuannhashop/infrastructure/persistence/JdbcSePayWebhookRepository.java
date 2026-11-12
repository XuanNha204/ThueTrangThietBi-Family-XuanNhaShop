package vn.xuannhashop.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.finance.application.SePayWebhookRepository;
import vn.xuannhashop.business.finance.domain.SePayTransfer;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

@Repository
public class JdbcSePayWebhookRepository implements SePayWebhookRepository {
    private final Sql s;private final ObjectMapper json;
    public JdbcSePayWebhookRepository(Sql s,ObjectMapper json){this.s=s;this.json=json;}
    private String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    public Receipt store(String channel,SePayTransfer t,byte[] raw){
        String fingerprint;try{fingerprint=hash(json.writeValueAsBytes(t));}catch(Exception ex){throw new IllegalStateException(ex);}
        String key=channel+":"+(t.transactionId()>0?"tx:"+t.transactionId():"sample:"+fingerprint);
        s.rows("select pg_advisory_xact_lock(hashtextextended(:key,0))",p("key","sepay:"+key));
        var old=s.one("select id,fingerprint from sepay_webhook_event where receipt_key=:key for update",p("key",key));
        String body=new String(raw,StandardCharsets.UTF_8);
        if(old!=null){
            UUID id=(UUID)old.get("id");boolean conflict=!old.get("fingerprint").equals(fingerprint);
            s.update("update sepay_webhook_event set delivery_count=delivery_count+1,last_received_at=clock_timestamp(),conflict_count=conflict_count+:extra,status=case when :conflict then 'CONFLICT' else status end where id=:id",p("id",id,"extra",conflict?1:0,"conflict",conflict));
            if(conflict)s.update("insert into sepay_webhook_conflict(id,event_id,fingerprint,raw_body) values(:id,:e,:f,:raw) on conflict(event_id,fingerprint) do nothing",p("id",UUID.randomUUID(),"e",id,"f",fingerprint,"raw",body));
            return new Receipt(id,false,conflict);
        }
        UUID id=UUID.randomUUID();
        s.update("""
            insert into sepay_webhook_event(id,channel,receipt_key,transaction_id,gateway,account_number,occurred_at,
              transfer_type,amount,payment_code,reference_code,fingerprint,body_sha256,raw_body,status)
            values(:id,:channel,:key,:tx,:bank,:account,:at,:direction,:amount,:code,:reference,:fingerprint,:hash,:raw,:status)
            """,p("id",id,"channel",channel,"key",key,"tx",t.transactionId(),"bank",t.gateway(),"account",t.accountNumber(),"at",t.occurredAt(),
                "direction",t.transferType(),"amount",t.amount(),"code",t.paymentCode(),"reference",t.referenceCode(),"fingerprint",fingerprint,"hash",hash(raw),"raw",body,"status",t.transactionId()==0?"TEST":"RECEIVED"));
        return new Receipt(id,true,false);
    }
    public List<Map<String,Object>> events(){return s.rows("""
        select e.id,e.channel,e.transaction_id::text transaction_id,e.gateway,'****'||right(e.account_number,4) account_masked,
          e.occurred_at,e.transfer_type,e.amount::text amount,e.payment_code,e.reference_code,e.status,e.delivery_count,
          e.conflict_count,e.received_at,e.last_received_at,e.reconciliation_status,e.reconciliation_reason,
          e.payment_request_id,e.money_id,e.reconciled_by,e.reconciled_at,e.resolution_note,p.code request_code,
          p.order_id,o.code order_code
        from sepay_webhook_event e left join bank_payment_request p on p.id=e.payment_request_id
          left join rental_order o on o.id=p.order_id
        order by e.last_received_at desc limit 100
        """,Map.of());}
    public Map<String,Object> statistics(){return s.one("select count(*) event_count,max(last_received_at) last_received_at from sepay_webhook_event",Map.of());}
}
