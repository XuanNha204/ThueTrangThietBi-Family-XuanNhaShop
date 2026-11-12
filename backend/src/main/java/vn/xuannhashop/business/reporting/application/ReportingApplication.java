package vn.xuannhashop.business.reporting.application;
import java.time.LocalDate;
import java.util.*;
public interface ReportingApplication { Map<String,Object> dashboard();List<Map<String,Object>> ledger(LocalDate from,LocalDate until); }
