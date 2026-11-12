package vn.xuannhashop.business.reporting.application;
import java.util.*;import java.time.*;
public interface BusinessPerformanceRepository {Map<String,Object> report(String period,Instant from,Instant until,LocalDate start,LocalDate end);}
