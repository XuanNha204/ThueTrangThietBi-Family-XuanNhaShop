package vn.xuannhashop.business.identity.application;
import java.util.*;import java.time.LocalDate;
public interface CustomerVerificationApplication {
    record Policy(Long expectedVersion,boolean required,String title,String criteria,String reason){}
    record Verification(UUID customerId,Long policyVersion,boolean passed,String method,String referenceHint,String note,LocalDate validUntil){}
    Map<String,Object> overview();Map<String,Object> publicPolicy();
    UUID publish(Policy input,String key);UUID verify(Verification input,String key);
    void requireEligible(UUID customer);
}
