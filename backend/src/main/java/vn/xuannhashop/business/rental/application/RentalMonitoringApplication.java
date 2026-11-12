package vn.xuannhashop.business.rental.application;
import java.util.*;
public interface RentalMonitoringApplication {
    List<Map<String,Object>> attention();
    List<Map<String,Object>> notices(UUID order);
    void monitor();
}
