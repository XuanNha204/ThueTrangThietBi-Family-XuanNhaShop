package vn.xuannhashop.business.identity.application;
import java.util.*;
import vn.xuannhashop.business.identity.application.CustomerVerificationApplication.*;
public interface CustomerVerificationRepository {
    void lockPolicy();Map<String,Object> policy();Map<String,Object> overview();
    Map<String,Object> customer(UUID id);Map<String,Object> latest(UUID customer);
    void publish(Policy c,String actor);void verify(UUID id,Verification c,long customerVersion,String actor);
}
