package vn.xuannhashop.business.migration.application;
import java.util.*;
public interface MigrationApplication { List<Map<String,Object>> list();Map<String,Object> detail(UUID id);UUID stage(byte[] bytes,String filename,String source,String key);void commit(UUID id,String key); }
