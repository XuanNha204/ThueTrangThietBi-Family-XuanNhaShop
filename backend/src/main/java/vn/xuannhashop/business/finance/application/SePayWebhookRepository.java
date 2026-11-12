package vn.xuannhashop.business.finance.application;
import java.util.*;
import vn.xuannhashop.business.finance.domain.SePayTransfer;
public interface SePayWebhookRepository {
    record Receipt(UUID id,boolean first,boolean conflict) {}
    Receipt store(String channel,SePayTransfer transfer,byte[] rawBody);
    List<Map<String,Object>> events();
    Map<String,Object> statistics();
}
