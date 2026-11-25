package vn.xuannhashop.business.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vn.xuannhashop.business.shared.DomainException;

@Service
@ConditionalOnProperty(name = "app.gmail.enabled", havingValue = "true")
public class GmailChallengeService {
    public static final int CODE_TTL_SECONDS = 300;
    public static final int RESEND_SECONDS = 60;
    private final SecureRandom random = new SecureRandom();
    private final GmailChallengeRepository repository;
    private final GmailCodeSender mail;
    private final byte[] key;

    public GmailChallengeService(GmailChallengeRepository repository, GmailCodeSender mail,
                                 @Value("${app.gmail.otp-key:}") String key) {
        if (key.length() < 32) throw new IllegalStateException("Gmail verification requires a separate signing key.");
        this.repository = repository;
        this.mail = mail;
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public void send(UUID challenge, String recipient) {
        String email = GmailAddress.required(recipient);
        if (!repository.reserveSend(HexFormat.of().formatHex(mac("inbox:" + email)))) {
            throw new DomainException(429, "Vui lòng đợi trước khi gửi lại mã. Mỗi Gmail nhận tối đa 5 mã trong 15 phút.");
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        mail.send(email, code);
        if (!repository.activate(challenge, mac("code:" + challenge + ":" + code))) {
            throw DomainException.conflict("Phiên đã được xác minh. Hãy đăng nhập lại nếu cần.");
        }
    }

    public void verify(UUID challenge, String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw DomainException.bad("Nhập mã gồm đúng 6 chữ số.");
        var result = repository.check(challenge, mac("code:" + challenge + ":" + code));
        if (result == null) throw DomainException.bad("Mã đã hết hạn hoặc hết lượt thử. Hãy gửi lại mã.");
        if (!result.accepted()) {
            if (result.remaining() == 0) throw DomainException.bad("Bạn đã dùng hết 5 lượt thử. Hãy gửi lại mã.");
            throw DomainException.bad("Mã chưa đúng. Bạn còn " + result.remaining() + " lượt thử.");
        }
    }

    private byte[] mac(String value) {
        try {
            var hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hmac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign authentication challenge", e);
        }
    }
}
