package vn.xuannhashop.infrastructure.partners;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.finance.application.SePayAuthenticator;
import vn.xuannhashop.business.shared.DomainException;

@Component
public class SePayHmacAuthenticator implements SePayAuthenticator {
    private final String secret,channel;private final boolean enabled;private final Clock clock;
    public SePayHmacAuthenticator(@Value("${app.sepay.webhook-secret:}")String secret,@Value("${app.sepay.enabled:false}")boolean enabled,
            @Value("${app.sepay.channel:dev}")String channel,Clock clock){
        this.secret=secret;this.enabled=enabled;this.channel=channel;this.clock=clock;
        if(!channel.matches("[A-Za-z0-9_-]{1,40}"))throw new IllegalArgumentException("Invalid SePay channel");
        if(enabled&&(secret.length()<16||secret.length()>1024))throw new IllegalArgumentException("SePay webhook secret must contain 16 to 1024 characters");
    }
    public boolean ready(){return enabled&&!secret.isBlank();}
    public String channel(){return channel;}
    private DomainException unauthorized(){return new DomainException(401,"Webhook không được xác thực.");}
    public void verify(byte[] rawBody,String timestamp,String signature){
        if(!ready())throw new DomainException(503,"Bộ nhận SePay chưa được cấu hình.");
        if(timestamp==null||!timestamp.matches("[0-9]{1,12}")||signature==null||!signature.matches("sha256=[0-9a-fA-F]{64}"))throw unauthorized();
        long time=Long.parseLong(timestamp),now=clock.instant().getEpochSecond();
        if(time<now-300||time>now+300)throw unauthorized();
        try{
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));mac.update((byte)'.');byte[] expected=mac.doFinal(rawBody);
            if(!MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature.substring(7))))throw unauthorized();
        }catch(java.security.GeneralSecurityException ex){throw new IllegalStateException("HMAC validation unavailable",ex);}
    }
}
