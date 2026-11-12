package vn.xuannhashop.business.migration.pipeline;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.shared.DomainException;
@Component
public class SourceReader {
    public List<Map<String,String>> read(byte[] bytes){
        if(bytes==null||bytes.length==0||bytes.length>1024*1024)throw DomainException.bad("CSV phải có nội dung và không vượt 1 MB.");
        try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replaceFirst("^\\uFEFF","");
            var format=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build();
            try(var parser=CSVParser.parse(text,format)){if(!parser.getHeaderNames().equals(List.of("sourceId","name","phone","address")))throw DomainException.bad("CSV cần đúng các cột: sourceId,name,phone,address.");var rows=new ArrayList<Map<String,String>>();for(var record:parser){if(rows.size()>=1000)throw DomainException.bad("Bản dev hỗ trợ tối đa 1.000 dòng mỗi batch.");if(!record.isConsistent())throw DomainException.bad("CSV có dòng thiếu hoặc thừa cột.");rows.add(record.toMap());}if(rows.isEmpty())throw DomainException.bad("CSV chưa có dòng dữ liệu.");return rows;}
        }catch(DomainException e){throw e;}catch(Exception e){throw DomainException.bad("Không đọc được CSV UTF-8. Kiểm tra dấu nháy, số cột và mã hóa tệp.");}
    }
}
