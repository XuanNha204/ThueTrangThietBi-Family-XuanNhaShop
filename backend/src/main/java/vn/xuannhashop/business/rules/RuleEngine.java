package vn.xuannhashop.business.rules;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.shared.DomainException;
@Component
public class RuleEngine {
    public <T> T select(Map<String,T> registry,String version){var rule=registry.get(version);if(rule==null)throw DomainException.bad("Phiên bản chính sách chưa được hỗ trợ: "+version);return rule;}
}
