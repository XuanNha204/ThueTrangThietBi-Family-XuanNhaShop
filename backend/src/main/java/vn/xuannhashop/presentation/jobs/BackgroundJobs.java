package vn.xuannhashop.presentation.jobs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.xuannhashop.business.rental.application.RentalApplication;
import vn.xuannhashop.business.support.application.SupportApplication;
import vn.xuannhashop.business.rental.application.RentalMonitoringApplication;
@Component
@ConditionalOnProperty(name="app.jobs-enabled",havingValue="true",matchIfMissing=true)
public class BackgroundJobs {
    private static final Logger log=LoggerFactory.getLogger(BackgroundJobs.class);
    private final RentalApplication rentals;private final SupportApplication support;
    private final RentalMonitoringApplication monitoring;private final Executor worker;private final AtomicBoolean sending=new AtomicBoolean();
    public BackgroundJobs(RentalApplication r,SupportApplication s,RentalMonitoringApplication monitoring,@Qualifier("backgroundWorker") Executor worker){rentals=r;support=s;this.monitoring=monitoring;this.worker=worker;}
    @Scheduled(fixedDelay=15000) public void expire(){rentals.expireHolds();}
    @Scheduled(fixedDelay=60000) public void monitor(){monitoring.monitor();}
    @Scheduled(fixedDelay=10000) public void deliver(){
        if(!sending.compareAndSet(false,true))return;
        try{worker.execute(()->{try{support.deliverNotifications();}catch(Exception e){log.error("Notification worker failed; durable jobs remain retryable",e);}finally{sending.set(false);}});}
        catch(java.util.concurrent.RejectedExecutionException e){sending.set(false);log.warn("Background queue is full; notification dispatch will retry");}
    }
}
