package vn.xuannhashop.business.finance.application;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class SePayPaymentService implements SePayPaymentApplication {
    private static final Logger log=LoggerFactory.getLogger(SePayPaymentService.class);
    private final SePayPaymentRepository r;private final FinanceRepository finance;
    private final SePayPaymentWorker worker;private final SePayAuthenticator auth;
    private final ActorContext actor;private final Commands commands;private final ActivityService audit;private final Clock clock;
    public SePayPaymentService(SePayPaymentRepository r,FinanceRepository finance,SePayPaymentWorker worker,SePayAuthenticator auth,ActorContext actor,Commands commands,ActivityService audit,Clock clock){this.r=r;this.finance=finance;this.worker=worker;this.auth=auth;this.actor=actor;this.commands=commands;this.audit=audit;this.clock=clock;}
    public Map<String,Object> configuration(){actor.require("OWNER","ACCOUNTANT");return r.configuration(false);}
    @Transactional public void configure(BankConfiguration c,String key){
        actor.require("OWNER");if(c==null)throw DomainException.bad("Thiếu cấu hình.");
        String gateway=Checks.text(c.gateway(),40,"Ngân hàng"),account=Checks.text(c.accountNumber(),30,"Số tài khoản"),holder=Checks.text(c.accountHolder(),120,"Chủ tài khoản");
        if(!gateway.matches("[A-Za-z][A-Za-z0-9]{1,39}")||!account.matches("[0-9]{4,30}"))throw DomainException.bad("Dùng mã ngân hàng (ví dụ MBBank) và số tài khoản chỉ gồm chữ số.");
        if(c.enabled()&&!auth.ready())throw DomainException.conflict("Webhook HMAC chưa sẵn sàng.");
        commands.once(key,"SEPAY_CONFIG:"+c,()->{
            var current=r.configuration(true);if(((Number)current.get("version")).longValue()!=c.version())throw DomainException.conflict("Cấu hình đã thay đổi. Hãy tải lại.");
            r.configure(new BankConfiguration(c.version(),gateway,account,holder,c.enabled(),c.automaticPosting()),actor.username());
            audit.record("SEPAY_BANK_CONFIGURED",null,"gateway="+gateway+"; account=****"+account.substring(account.length()-4)+"; enabled="+c.enabled()+"; auto="+c.automaticPosting());return UUID.randomUUID();
        });
    }
    private Map<String,Object> accessible(UUID order){
        var o=finance.lockOrder(order);if(o==null)throw DomainException.missing();var user=actor.current();
        if(user.role().equals("CUSTOMER")){if(!Objects.equals(user.customerId(),o.get("customer_id")))throw DomainException.missing();}
        else actor.require("OWNER","ACCOUNTANT");return o;
    }
    @Transactional public List<Map<String,Object>> requests(UUID order){
        var o=accessible(order);var config=r.configuration(false);var balance=finance.balances(order);Instant now=clock.instant();
        boolean active=!Set.of("CANCELLED","EXPIRED","COMPLETED").contains(o.get("status"))&&(!o.get("status").equals("HELD")||now.isBefore((Instant)o.get("expires_at")));
        var result=r.requests(order);for(var request:result){
            boolean payable=active&&auth.ready()&&Boolean.TRUE.equals(config.get("enabled"))&&request.get("status").equals("OPEN")&&now.isBefore((Instant)request.get("expires_at"))
                &&Objects.equals(request.get("gateway"),config.get("gateway"))&&Objects.equals(request.get("account_number"),config.get("account_number"))
                &&new BigDecimal((String)request.get("amount")).compareTo(SePayPaymentWorker.remaining(balance,o,(String)request.get("kind")))==0;
            request.put("payable",payable);
        }return result;
    }
    @Transactional public UUID create(UUID order,String kind,String key){
        actor.require("OWNER","ACCOUNTANT","CUSTOMER");
        return commands.once(key,"BANK_REQUEST:"+order+":"+kind,()->{
            var config=r.configuration(true);var o=accessible(order);
            if(!Boolean.TRUE.equals(config.get("enabled"))||!auth.ready())throw DomainException.conflict("Cửa hàng chưa bật tài khoản nhận SePay.");
            Instant now=clock.instant(),expires=now.plusSeconds(1800);String status=(String)o.get("status");
            if(Set.of("CANCELLED","EXPIRED","COMPLETED").contains(status))throw DomainException.conflict("Đơn đã đóng, không tạo yêu cầu chuyển khoản mới.");
            if(status.equals("HELD")){Instant heldUntil=(Instant)o.get("expires_at");if(!now.isBefore(heldUntil))throw DomainException.conflict("Đơn đã hết giữ chỗ.");if(heldUntil.isBefore(expires))expires=heldUntil;}
            BigDecimal amount=SePayPaymentWorker.remaining(finance.balances(order),o,kind);Checks.money(amount);
            if(amount.stripTrailingZeros().scale()>0)throw DomainException.bad("Thanh toán ngân hàng cần số tiền VND nguyên.");
            r.expireRequests(order,now);
            var old=r.openRequest(order,kind);if(old!=null){
                if(((BigDecimal)old.get("amount")).compareTo(amount)==0&&Objects.equals(old.get("gateway"),config.get("gateway"))&&Objects.equals(old.get("account_number"),config.get("account_number")))return (UUID)old.get("id");
                r.cancel((UUID)old.get("id"));
            }
            UUID id=UUID.randomUUID();String code="XN"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase(Locale.ROOT);
            var values=new HashMap<String,Object>();values.put("id",id);values.put("order_id",order);values.put("code",code);values.put("kind",kind);values.put("amount",amount);
            for(String field:List.of("gateway","account_number","account_holder"))values.put(field,config.get(field));
            values.put("created_by",actor.username());values.put("created_at",now);values.put("expires_at",expires);
            r.insert(values);audit.record("BANK_PAYMENT_REQUESTED",order,kind+"; code="+code+"; amount="+amount);return id;
        });
    }
    @Transactional public void cancel(UUID id,String key){actor.require("OWNER","ACCOUNTANT","CUSTOMER");commands.once(key,"BANK_CANCEL:"+id,()->{
        var request=r.request(id,false);if(request==null)throw DomainException.missing();accessible((UUID)request.get("order_id"));request=r.request(id,true);
        if(!request.get("status").equals("OPEN"))throw DomainException.conflict("Yêu cầu không còn mở.");r.cancel(id);audit.record("BANK_PAYMENT_CANCELLED",(UUID)request.get("order_id"),"request="+id);return id;
    });}
    @Transactional public UUID reconcile(UUID event,UUID request,String reason,String key){actor.require("OWNER","ACCOUNTANT");if(request==null)throw DomainException.bad("Chọn yêu cầu thanh toán.");String note=Checks.text(reason,300,"Căn cứ đối soát");return commands.once(key,"BANK_MATCH:"+event+":"+request+":"+note,()->worker.process(event,request,note,actor.username()));}
    @Transactional public void ignore(UUID event,String reason,String key){actor.require("OWNER","ACCOUNTANT");String note=Checks.text(reason,500,"Lý do không ghi sổ");commands.once(key,"BANK_IGNORE:"+event+":"+note,()->{
        var e=r.event(event);if(e==null)throw DomainException.missing();if(e.get("money_id")!=null)throw DomainException.conflict("Đã ghi sổ; không thể bỏ qua hoặc xóa khoản tiền.");
        r.disposition(event,"IGNORED","MANUAL_IGNORE",(UUID)e.get("payment_request_id"),null,actor.username(),note);audit.record("SEPAY_EVENT_IGNORED",event,note);return event;
    });}
    @Transactional public void retry(UUID event,String key){actor.require("OWNER","ACCOUNTANT");commands.once(key,"BANK_RETRY:"+event,()->{
        var e=r.event(event);if(e==null)throw DomainException.missing();if(e.get("money_id")==null){r.disposition(event,"PENDING","PENDING",null,null,actor.username(),null);audit.record("SEPAY_RECHECK_REQUESTED",event,"Kiểm tra lại theo cấu hình hiện hành");}return event;
    });}
    public void processPending(){for(UUID id:r.pending())try{worker.process(id,null,null,"SYSTEM");}catch(Exception ex){log.warn("SePay receipt {} could not be reconciled; remains retryable ({})",id,ex.getClass().getSimpleName());}}
}
