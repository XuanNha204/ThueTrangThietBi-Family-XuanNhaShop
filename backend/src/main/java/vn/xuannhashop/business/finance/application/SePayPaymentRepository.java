package vn.xuannhashop.business.finance.application;

import java.util.*;

public interface SePayPaymentRepository {
    Map<String,Object> configuration(boolean lock);
    void configure(SePayPaymentApplication.BankConfiguration value,String actor);
    List<Map<String,Object>> requests(UUID order);
    Map<String,Object> request(UUID id,boolean lock);
    Map<String,Object> requestByCode(String code);
    Map<String,Object> openRequest(UUID order,String kind);
    void expireRequests(UUID order,java.time.Instant now);
    void insert(Map<String,Object> request);
    void cancel(UUID id);
    void paid(UUID id,UUID money);
    Map<String,Object> event(UUID id);
    List<UUID> pending();
    void disposition(UUID event,String status,String reason,UUID request,UUID money,String actor,String note);
}
