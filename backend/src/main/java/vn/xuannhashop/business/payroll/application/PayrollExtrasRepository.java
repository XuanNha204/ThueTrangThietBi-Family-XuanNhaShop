package vn.xuannhashop.business.payroll.application;
import java.util.*;import vn.xuannhashop.business.payroll.application.PayrollExtrasApplication.Adjustment;
public interface PayrollExtrasRepository {
    Map<String,Object> overview();Map<String,Object> mine(UUID account);
    boolean linkable(UUID account);void link(UUID staff,UUID account);
    void adjustment(UUID id,Adjustment c,String actor);Map<String,Object> adjustment(UUID id);
    void decide(UUID id,boolean accepted,String actor,String note);
    boolean owns(UUID account,UUID payroll);void question(UUID id,UUID payroll,String question,String actor);
    Map<String,Object> question(UUID id);void answer(UUID id,String answer,String actor);
}
