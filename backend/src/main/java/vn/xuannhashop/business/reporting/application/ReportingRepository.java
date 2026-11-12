package vn.xuannhashop.business.reporting.application;
import java.time.LocalDate;
import java.util.*;
public interface ReportingRepository {
    Map<String,Object> operations();Map<String,Object> finance();
    List<Map<String,Object>> trend();List<Map<String,Object>> category();
    List<Map<String,Object>> ledger(LocalDate from,LocalDate until);
    void export(String actor,String scope);
}
