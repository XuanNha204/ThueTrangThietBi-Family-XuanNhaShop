package vn.xuannhashop.business.maintenance.application;
import java.util.*;
public interface InspectionChecklistApplication {
    record Criterion(String code,String label,Boolean blocking) {}
    record Template(UUID typeId,long version,List<Criterion> checks) {}
    record Publish(Long expectedVersion,List<Criterion> checks,String reason) {}
    record Answer(String code,Boolean passed,String note) {}
    record Input(boolean powerSafe,boolean clean,boolean complete,String findings,Long checklistVersion,List<Answer> answers) {}
    record Result(String code,String label,boolean blocking,boolean passed,String note) {}
    Template current(UUID type);
    List<Map<String,Object>> history(UUID type);
    UUID publish(UUID type,Publish input,String key);
}
