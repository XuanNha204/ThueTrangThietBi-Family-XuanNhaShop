package vn.xuannhashop.business.finance.application;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.finance.domain.SePayTransfer;
import vn.xuannhashop.business.shared.audit.ActivityService;

@Service
public class SePayInboxWriter {
    private final SePayWebhookRepository repository;private final ActivityService audit;
    public SePayInboxWriter(SePayWebhookRepository repository,ActivityService audit){this.repository=repository;this.audit=audit;}
    @Transactional public void store(String channel,SePayTransfer transfer,byte[] rawBody){
        var receipt=repository.store(channel,transfer,rawBody);
        if(receipt.first()||receipt.conflict())audit.system(receipt.conflict()?"SEPAY_PAYLOAD_CONFLICT":"SEPAY_WEBHOOK_RECEIVED",receipt.id(),
            "channel="+channel+"; transactionId="+transfer.transactionId()+"; inbox stored, reconciliation pending");
    }
}
