package vn.xuannhashop.presentation.api;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import vn.xuannhashop.business.shared.DomainException;
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    private ResponseEntity<Map<String,Object>> response(int status,String message){return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Map.of("message",message,"status",status));}
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,org.springframework.web.servlet.NoHandlerFoundException.class}) ResponseEntity<?> missing(){return response(404,"Không tìm thấy địa chỉ yêu cầu.");}
    @ExceptionHandler(DomainException.class) ResponseEntity<?> domain(DomainException e){return response(e.status(),e.getMessage());}
    @ExceptionHandler(AuthenticationException.class) ResponseEntity<?> auth(){return response(401,"Tên đăng nhập hoặc mật khẩu không đúng.");}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> integrity(){return response(409,"Dữ liệu trùng hoặc không còn phù hợp. Hãy tải lại và kiểm tra.");}
    @ExceptionHandler({CannotAcquireLockException.class,QueryTimeoutException.class}) ResponseEntity<?> locked(){return response(409,"Dữ liệu đang được xử lý bởi yêu cầu khác. Hãy kiểm tra lại trạng thái trước khi thử lại.");}
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,MethodArgumentNotValidException.class}) ResponseEntity<?> bad(Exception e){return response(400,"Dữ liệu gửi lên thiếu hoặc không đúng định dạng.");}
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> large(){return response(413,"Tệp vượt giới hạn dung lượng cho phép.");}
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class) ResponseEntity<?> media(){return response(415,"Content-Type không được hỗ trợ. Webhook SePay dùng application/json.");}
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class) ResponseEntity<?> method(){return response(405,"Phương thức HTTP không được hỗ trợ.");}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e){String id=UUID.randomUUID().toString();log.error("Request failed; reference {}",id,e);return response(500,"Không xử lý được yêu cầu. Mã tra cứu: "+id);}
}
