package vn.xuannhashop.business.reporting.application;
import java.time.Instant;
import java.util.*;
public interface EquipmentHistoryApplication { Map<String,Object> history(UUID equipment,Instant before,String beforeKey); }
