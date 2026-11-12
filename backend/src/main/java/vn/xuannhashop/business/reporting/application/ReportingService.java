package vn.xuannhashop.business.reporting.application;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.shared.security.ActorContext;
import vn.xuannhashop.business.shared.DomainException;
@Service
public class ReportingService implements ReportingApplication {
    private final ReportingRepository r;private final ActorContext actor;
    public ReportingService(ReportingRepository r,ActorContext actor){this.r=r;this.actor=actor;}
    public Map<String,Object> dashboard(){actor.require("OWNER","MANAGER","ACCOUNTANT","INVENTORY","DELIVERY","TECHNICIAN","HR");var result=new LinkedHashMap<String,Object>(r.operations());result.put("trend",r.trend());result.put("categories",r.category());result.put("asOf",java.time.Instant.now());if(Set.of("OWNER","ACCOUNTANT").contains(actor.current().role()))result.putAll(r.finance());return result;}
    @Transactional public List<Map<String,Object>> ledger(LocalDate from,LocalDate until){actor.require("OWNER","ACCOUNTANT");if(from==null||until==null||until.isBefore(from)||ChronoUnit.DAYS.between(from,until)>366)throw DomainException.bad("Chọn kỳ báo cáo hợp lệ, tối đa 366 ngày.");var rows=r.ledger(from,until);if(rows.size()>10000)throw DomainException.bad("Báo cáo vượt 10.000 dòng. Chọn kỳ ngắn hơn để xuất đầy đủ.");r.export(actor.username(),from+" → "+until);return rows;}
}
