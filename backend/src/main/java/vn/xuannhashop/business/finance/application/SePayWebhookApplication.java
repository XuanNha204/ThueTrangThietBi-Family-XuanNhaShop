package vn.xuannhashop.business.finance.application;

import java.util.*;

public interface SePayWebhookApplication {
    String PATH="/api/integrations/sepay/webhook";
    int MAX_BODY_BYTES=65536;
    void receive(byte[] rawBody,String timestamp,String signature);
    Map<String,Object> status();
    List<Map<String,Object>> events();
}
