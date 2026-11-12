package vn.xuannhashop.presentation.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.finance.application.SePayPaymentApplication;

@Component
@ConditionalOnProperty(name="app.jobs-enabled",havingValue="true",matchIfMissing=true)
public class SePayReconciliationJob {
    private final SePayPaymentApplication payments;
    public SePayReconciliationJob(SePayPaymentApplication payments){this.payments=payments;}
    @Scheduled(fixedDelay=10000) public void reconcile(){payments.processPending();}
}
