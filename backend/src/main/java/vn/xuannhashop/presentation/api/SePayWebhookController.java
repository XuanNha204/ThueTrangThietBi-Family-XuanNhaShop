package vn.xuannhashop.presentation.api;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import vn.xuannhashop.business.finance.application.SePayWebhookApplication;

@RestController
public class SePayWebhookController {
    private final SePayWebhookApplication sepay;
    public SePayWebhookController(SePayWebhookApplication sepay){this.sepay=sepay;}
    @PostMapping(value=SePayWebhookApplication.PATH,consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    Map<String,Boolean> receive(HttpServletRequest request,
            @RequestHeader(value="X-SePay-Timestamp",required=false)String timestamp,
            @RequestHeader(value="X-SePay-Signature",required=false)String signature)throws IOException{
        byte[] raw=request.getInputStream().readNBytes(SePayWebhookApplication.MAX_BODY_BYTES+1);
        sepay.receive(raw,timestamp,signature);return Map.of("success",true);
    }
    @GetMapping("/api/finance/sepay/status") Object status(){return sepay.status();}
    @GetMapping("/api/finance/sepay/events") Object events(){return sepay.events();}
}
