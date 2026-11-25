package vn.xuannhashop.business.identity.application;

public interface GmailCodeSender {
    void send(String recipient, String code);
}
