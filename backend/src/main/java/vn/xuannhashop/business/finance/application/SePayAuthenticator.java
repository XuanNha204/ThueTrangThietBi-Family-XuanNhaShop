package vn.xuannhashop.business.finance.application;
public interface SePayAuthenticator {
    void verify(byte[] rawBody,String timestamp,String signature);
    boolean ready();
    String channel();
}
