package vn.xuannhashop.business.payroll.application;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public interface PayrollRepository {
    List<Map<String,Object>> staff();List<Map<String,Object>> work();List<Map<String,Object>> payroll();
    Map<String,Object> lockStaff(UUID id); Map<String,Object> lockWork(UUID id); Map<String,Object> lockPayroll(UUID id);
    boolean periodLocked(UUID staff,String period); BigDecimal approvedHours(UUID staff,String period);
    void work(UUID id,UUID staff,LocalDate date,BigDecimal hours,String actor);void approveWork(UUID id,String actor);
    void draft(UUID id,UUID staff,String period,BigDecimal hours,BigDecimal rate,BigDecimal amount,String actor);
    void status(UUID id,String status,String actor);
    boolean pendingAdjustments(UUID staff,String period);BigDecimal adjustmentTotal(UUID staff,String period);
}
