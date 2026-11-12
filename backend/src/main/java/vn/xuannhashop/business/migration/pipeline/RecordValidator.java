package vn.xuannhashop.business.migration.pipeline;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.migration.application.MigrationRepository;
@Component
public class RecordValidator {
    private final MigrationRepository r;public RecordValidator(MigrationRepository r){this.r=r;}
    public String validate(Map<String,String> row,Set<String> sources,Set<String> phones){var errors=new ArrayList<String>();String source=row.get("sourceId"),phone=row.get("phone");if(!source.matches("[A-Za-z0-9._-]{1,60}"))errors.add("Mã nguồn không hợp lệ");if(!sources.add(source)||r.sourceExists(source))errors.add("Trùng mã nguồn");if(row.get("name").isBlank()||row.get("name").length()>120)errors.add("Họ tên không hợp lệ");if(!phone.matches("\\+?[0-9]{8,15}"))errors.add("Điện thoại không hợp lệ");if(!phones.add(phone)||r.phoneExists(phone))errors.add("Điện thoại đã có: cần đối chiếu thủ công");if(row.get("address").isBlank()||row.get("address").length()>400)errors.add("Địa chỉ không hợp lệ");return String.join("; ",errors);}
}
