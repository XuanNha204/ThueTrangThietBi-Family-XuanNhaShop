package vn.xuannhashop.business.finance.application;

import java.util.*;

public interface SePayPaymentApplication {
    record BankConfiguration(long version,String gateway,String accountNumber,String accountHolder,boolean enabled,boolean automaticPosting) {}
    Map<String,Object> configuration();
    void configure(BankConfiguration configuration,String key);
    List<Map<String,Object>> requests(UUID order);
    UUID create(UUID order,String kind,String key);
    void cancel(UUID request,String key);
    UUID reconcile(UUID event,UUID request,String reason,String key);
    void ignore(UUID event,String reason,String key);
    void retry(UUID event,String key);
    void processPending();
}
