package vn.xuannhashop.infrastructure.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.identity.application.GmailAddress;
import vn.xuannhashop.business.identity.application.GmailCodeSender;
import vn.xuannhashop.business.shared.DomainException;

@Component
@ConditionalOnProperty(name = "app.gmail.smtp.enabled", havingValue = "true")
class SmtpGmailCodeSender implements GmailCodeSender {
    private final JavaMailSender mail;
    private final String sender;

    SmtpGmailCodeSender(JavaMailSender mail, @Value("${spring.mail.username:}") String sender,
                        @Value("${spring.mail.password:}") String password) {
        GmailAddress.required(sender);
        if (password.isBlank()) throw new IllegalStateException("Gmail SMTP app password is required.");
        this.mail = mail;
        this.sender = sender;
    }

    @Override
    public void send(String recipient, String code) {
        MimeMessagePreparator prepare = message -> {
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(message, "UTF-8");
            helper.setFrom(sender, "XuanNhaShop");
            helper.setTo(recipient);
            helper.setSubject("Mã xác minh XuanNhaShop");
            helper.setText("Mã xác minh của bạn là " + code + ". Mã có hiệu lực trong 5 phút và chỉ dùng một lần.\n"
                + "Nếu bạn không yêu cầu mã, hãy bỏ qua email này.");
        };
        try {
            mail.send(prepare);
        } catch (MailException e) {
            throw new DomainException(503, "Chưa gửi được mã Gmail. Vui lòng thử lại sau.");
        }
    }
}
