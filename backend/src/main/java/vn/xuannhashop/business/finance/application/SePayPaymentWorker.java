package vn.xuannhashop.business.finance.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;

/** One database transaction per receipt; retries never post a second money row. */
@Service
public class SePayPaymentWorker {
    private static final Pattern CODE=Pattern.compile("(?<![A-Z0-9])XN[A-F0-9]{24}(?![A-Z0-9])");
    private final SePayPaymentRepository r;
    private final FinanceRepository finance;
    private final SePayPayloadReader parser;
    private final ActivityService audit;
    private final Clock clock;
    public SePayPaymentWorker(SePayPaymentRepository r,FinanceRepository finance,SePayPayloadReader parser,ActivityService audit,Clock clock){this.r=r;this.finance=finance;this.parser=parser;this.audit=audit;this.clock=clock;}

    @Transactional public UUID process(UUID id,UUID selected,String note,String actor){
        boolean manual=selected!=null;
        var e=r.event(id);if(e==null)throw DomainException.missing();
        if(e.get("money_id")!=null){
            if(manual&&!selected.equals(e.get("payment_request_id")))throw DomainException.conflict("Giao dịch đã ghi cho yêu cầu khác.");
            return (UUID)e.get("money_id");
        }
        if(e.get("reconciliation_status").equals("IGNORED")&&!manual)return null;
        if(((Number)e.get("transaction_id")).longValue()==0){return review(id,"SAMPLE",null,manual,"TEST");}
        if(e.get("status").equals("CONFLICT"))return review(id,"CONFLICT",null,manual,"REVIEW");
        var t=parser.parse(((String)e.get("raw_body")).getBytes(StandardCharsets.UTF_8));
        if(!t.transferType().equals("in"))return review(id,"OUTGOING",null,manual,"REVIEW");
        var config=r.configuration(true);
        if(!Boolean.TRUE.equals(config.get("enabled")))return review(id,"CONFIG_DISABLED",null,manual,"REVIEW");
        if(!sameBank(t.gateway(),config.get("gateway"))||!t.accountNumber().equals(config.get("account_number")))return review(id,"WRONG_ACCOUNT",null,manual,"REVIEW");
        Map<String,Object> request;
        if(manual)request=r.request(selected,false);
        else {
            var codes=new HashSet<String>();var matcher=CODE.matcher(t.content().toUpperCase(Locale.ROOT));
            while(matcher.find())codes.add(matcher.group());
            if(t.paymentCode()!=null&&!t.paymentCode().isBlank())codes.add(t.paymentCode().strip().toUpperCase(Locale.ROOT));
            if(codes.size()!=1)return review(id,"UNKNOWN_CODE",null,false,"REVIEW");
            request=r.requestByCode(codes.iterator().next());
        }
        if(request==null)return review(id,"UNKNOWN_CODE",null,manual,"REVIEW");
        UUID requestId=(UUID)request.get("id"),order=(UUID)request.get("order_id");
        // Same order lock as manual receipts/refunds/settlement. Lock order before request.
        var o=finance.lockOrder(order);request=r.request(requestId,true);
        if(!sameBank(request.get("gateway"),t.gateway())||!request.get("account_number").equals(t.accountNumber()))return review(id,"WRONG_ACCOUNT",requestId,manual,"REVIEW");
        if(request.get("money_id")!=null)return review(id,"ALREADY_PAID",requestId,manual,"REVIEW");
        String kind=(String)request.get("kind"),state=(String)o.get("status");
        Instant now=clock.instant();
        Instant created=(Instant)request.get("created_at");
        // Observed MBBank receipts can report :00 for a transfer made later in that minute.
        // Only that bank/precision may use the request's creation-minute boundary;
        // exact request code/account/amount and all expiry/dedup guards still apply.
        boolean minutePrecision=sameBank(t.gateway(),"MBBank")&&t.occurredAt().equals(t.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
        Instant earliest=minutePrecision?created.truncatedTo(java.time.temporal.ChronoUnit.MINUTES):created.minusSeconds(1);
        if(t.occurredAt().isAfter(now.plusSeconds(300))||t.occurredAt().isBefore(earliest))return review(id,"INVALID_TIME",requestId,manual,"REVIEW");
        boolean closed=Set.of("CANCELLED","EXPIRED","COMPLETED").contains(state);
        boolean expiredHold=state.equals("HELD")&&!now.isBefore((Instant)o.get("expires_at"));
        if(manual){
            if(state.equals("COMPLETED")||((closed||expiredHold)&&!kind.equals("DEPOSIT_IN")))return review(id,"ORDER_CLOSED",requestId,true,"REVIEW");
        } else {
            if(closed||expiredHold)return review(id,"ORDER_CLOSED",requestId,false,"REVIEW");
            if(!request.get("status").equals("OPEN")||!now.isBefore((Instant)request.get("expires_at"))||!t.occurredAt().isBefore((Instant)request.get("expires_at")))return review(id,"LATE_PAYMENT",requestId,false,"REVIEW");
            if(t.amount().compareTo((BigDecimal)request.get("amount"))!=0)return review(id,"AMOUNT_MISMATCH",requestId,false,"REVIEW");
            if(!Boolean.TRUE.equals(config.get("automatic_posting")))return review(id,"AUTO_DISABLED",requestId,false,"REVIEW");
        }
        BigDecimal remaining=remaining(finance.balances(order),o,kind);
        if(t.amount().compareTo(remaining)>0)return review(id,"EXCEEDS_DUE",requestId,manual,"REVIEW");
        UUID money=UUID.randomUUID();
        String evidence="SePay #"+t.transactionId()+" / "+request.get("code")+(manual?" / "+note: " / tự đối chiếu");
        finance.record(money,order,null,kind,t.amount(),"sepay:"+id,actor,evidence,null);
        r.paid(requestId,money);r.disposition(id,"POSTED",manual?"MANUAL_MATCH":"EXACT_MATCH",requestId,money,actor,note);
        if(manual)audit.record("SEPAY_PAYMENT_POSTED",order,"event="+id+"; "+kind+"; "+t.amount()+"; "+note);
        else audit.system("SEPAY_PAYMENT_POSTED",order,"event="+id+"; "+kind+"; "+t.amount());
        audit.notifyOrder(order,"PAYMENT_RECORDED",kind+" / SePay / "+t.amount());
        return money;
    }
    static BigDecimal remaining(FinanceApplication.Balance b,Map<String,Object> o,String kind){if(kind==null)throw DomainException.bad("Chọn loại khoản thu.");return switch(kind){
        case "DEPOSIT_IN" -> ((BigDecimal)o.get("deposit_required")).subtract(b.depositHeld());
        case "RENTAL_IN" -> ((BigDecimal)o.get("rental_total")).subtract(b.rentalPaid());
        case "CHARGE_IN" -> b.chargesDue().subtract(b.chargesPaid());
        default -> throw DomainException.bad("Loại khoản thu không hợp lệ.");
    };}
    private boolean sameBank(Object a,Object b){return a!=null&&b!=null&&a.toString().equalsIgnoreCase(b.toString());}
    private UUID review(UUID event,String reason,UUID request,boolean manual,String status){
        if(manual)throw DomainException.conflict("Chưa thể ghi nhận giao dịch: "+reason+". Kiểm tra tài khoản, nghĩa vụ và bản ghi gốc.");
        r.disposition(event,status,reason,request,null,"SYSTEM",null);return null;
    }
}
