package vn.xuannhashop.business.migration.pipeline;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class RecordNormalizer { public Map<String,String> normalize(Map<String,String> row){var m=new LinkedHashMap<String,String>();row.forEach((k,v)->m.put(k,v.strip()));m.put("phone",m.get("phone").replace(" ",""));return m;} }
