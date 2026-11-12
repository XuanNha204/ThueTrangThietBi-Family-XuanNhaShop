package vn.xuannhashop.business.payroll.application;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public interface PayrollApplication {
    Map<String,Object> overview();UUID work(UUID staff,LocalDate date,BigDecimal hours,String key);
    void approveWork(UUID id,String key);UUID draft(UUID staff,String period,String key);
    void approve(UUID id,String key);void pay(UUID id,String key);
}
