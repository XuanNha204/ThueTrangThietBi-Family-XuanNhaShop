package vn.xuannhashop.business.migration.pipeline;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.migration.domain.ImportRow;
@Component
public class DomainImportMapper {public ImportRow map(Map<String,String> r,int no,String errors){return new ImportRow(UUID.randomUUID(),no,r.get("sourceId"),r.get("name"),r.get("phone"),r.get("address"),errors);}}
