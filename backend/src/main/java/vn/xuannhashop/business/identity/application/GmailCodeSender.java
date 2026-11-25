package vn.xuannhashop.business.identity.application;

public interface GmailCodeSender {
    void send(String recipient, String code);
    default void sendRecovery(String recipient, String code) { send(recipient, code); }
}
