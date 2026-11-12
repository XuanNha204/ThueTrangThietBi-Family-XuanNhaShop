package vn.xuannhashop.business.finance.application;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class SePayWebhookService implements SePayWebhookApplication {
    private final SePayAuthenticator authentication;private final SePayPayloadReader parser;
    private final SePayInboxWriter inbox;private final SePayWebhookRepository repository;private final ActorContext actor;private final SePayPaymentRepository payments;
    public SePayWebhookService(SePayAuthenticator authentication,SePayPayloadReader parser,SePayInboxWriter inbox,SePayWebhookRepository repository,ActorContext actor,SePayPaymentRepository payments){this.authentication=authentication;this.parser=parser;this.inbox=inbox;this.repository=repository;this.actor=actor;this.payments=payments;}
    public void receive(byte[] rawBody,String timestamp,String signature){
        if(rawBody==null||rawBody.length==0)throw DomainException.bad("Webhook không có nội dung.");
        if(rawBody.length>MAX_BODY_BYTES)throw new DomainException(413,"Webhook vượt giới hạn dung lượng.");
        // Authenticate before parsing or opening the receipt transaction.
        authentication.verify(rawBody,timestamp,signature);
        var transfer=parser.parse(rawBody);inbox.store(authentication.channel(),transfer,rawBody);
    }
    public Map<String,Object> status(){
        actor.require("OWNER","ACCOUNTANT");var result=new LinkedHashMap<String,Object>(repository.statistics());
        var config=payments.configuration(false);boolean enabled=Boolean.TRUE.equals(config.get("enabled"));boolean automatic=enabled&&Boolean.TRUE.equals(config.get("automatic_posting"));
        result.put("ready",authentication.ready());result.put("authentication","HMAC-SHA256");result.put("mode",automatic?"AUTO_MATCH":enabled?"MANUAL_MATCH":"RECEIVE_ONLY");
        result.put("channel",authentication.channel());result.put("endpoint",PATH);result.put("automaticPosting",automatic);return result;
    }
    public List<Map<String,Object>> events(){actor.require("OWNER","ACCOUNTANT");return repository.events();}
}
