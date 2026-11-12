package vn.xuannhashop.business.migration.application;
import java.util.*;
import vn.xuannhashop.business.migration.domain.ImportRow;
public interface MigrationRepository {
    List<Map<String,Object>> list(String actor);Map<String,Object> batch(UUID id,boolean lock);List<Map<String,Object>> rows(UUID id);
    void stage(UUID id,String source,String filename,String actor,List<ImportRow> rows);void summarize(UUID id,int total,int valid);
    boolean sourceExists(String source);boolean phoneExists(String phone);void lockSources(List<String> sources);
    void link(UUID row,String source,UUID customer);void committed(UUID batch,String approver);
}
