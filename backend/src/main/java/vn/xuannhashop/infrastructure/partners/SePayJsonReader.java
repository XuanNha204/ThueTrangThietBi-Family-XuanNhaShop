package vn.xuannhashop.infrastructure.partners;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.finance.application.SePayPayloadReader;
import vn.xuannhashop.business.finance.domain.SePayTransfer;
import vn.xuannhashop.business.shared.DomainException;

@Component
public class SePayJsonReader implements SePayPayloadReader {
    private final ObjectMapper json;
    public SePayJsonReader(ObjectMapper mapper){json=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);}
    private String text(JsonNode body,String field,int max,boolean required){
        var value=body.get(field);if(value==null||value.isNull()){if(required)throw DomainException.bad("Webhook thiếu trường "+field+".");return null;}
        if(!value.isTextual()||value.textValue().length()>max||(required&&value.textValue().isBlank()))throw DomainException.bad("Trường "+field+" không hợp lệ.");return value.textValue();
    }
    public SePayTransfer parse(byte[] raw){
        try{
            var n=json.readTree(raw);if(n==null||!n.isObject())throw DomainException.bad("Webhook phải là JSON object.");
            var id=n.get("id");if(id==null||!id.isIntegralNumber()||!id.canConvertToLong()||id.longValue()<0)throw DomainException.bad("ID giao dịch không hợp lệ.");
            String direction=text(n,"transferType",3,true);if(!Set.of("in","out").contains(direction))throw DomainException.bad("Chiều giao dịch không hợp lệ.");
            var amount=n.get("transferAmount");if(amount==null||!amount.isNumber())throw DomainException.bad("Số tiền không hợp lệ.");
            BigDecimal money=amount.decimalValue().stripTrailingZeros();if(money.signum()<=0||money.scale()>0||money.compareTo(new BigDecimal("999999999999999999"))>0)throw DomainException.bad("Số tiền VND phải là số nguyên dương trong giới hạn hỗ trợ.");
            String date=text(n,"transactionDate",30,true);
            Instant at=LocalDateTime.parse(date,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            return new SePayTransfer(id.longValue(),text(n,"gateway",100,true),text(n,"accountNumber",100,true),at,direction,money.setScale(0),
                text(n,"code",200,false),text(n,"content",4000,true),text(n,"referenceCode",200,false),text(n,"subAccount",250,false));
        }catch(java.io.IOException|DateTimeException ex){throw DomainException.bad("Dữ liệu JSON hoặc thời gian webhook không hợp lệ.");}
    }
}
