package vn.xuannhashop;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.xuannhashop.business.finance.application.SePayWebhookApplication;
import vn.xuannhashop.infrastructure.partners.SePayHmacAuthenticator;
import vn.xuannhashop.business.shared.DomainException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"app.jobs-enabled=false","app.sepay.enabled=true","app.sepay.webhook-secret=sepay-junit-only-secret-2026-abcdef","app.sepay.channel=junit"})
@AutoConfigureMockMvc
class SePayWebhookTest {
    private static final String SECRET="sepay-junit-only-secret-2026-abcdef",PATH=SePayWebhookApplication.PATH;
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;
    String payload(long id,String reference,int amount){return "{\"id\":"+id+",\"gateway\":\"MBBank\",\"accountNumber\":\"000000001\",\"transactionDate\":\"2026-11-06 12:30:00\",\"transferType\":\"in\",\"transferAmount\":"+amount+",\"code\":null,\"content\":\"Kiểm thử nhận webhook — không thu tiền\",\"referenceCode\":\""+reference+"\",\"subAccount\":\"\"}";}
    long id(){return Math.abs(UUID.randomUUID().getMostSignificantBits()>>>1);}
    String ref(){return "JUNIT-"+UUID.randomUUID();}
    String timestamp(){return String.valueOf(Instant.now().getEpochSecond());}
    String signature(byte[] raw,String timestamp)throws Exception{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.US_ASCII));return "sha256="+HexFormat.of().formatHex(mac.doFinal(raw));}
    String signature(String raw,String timestamp)throws Exception{return signature(raw.getBytes(StandardCharsets.UTF_8),timestamp);}
    void send(String raw)throws Exception{String time=timestamp();mvc.perform(post(PATH).contentType("application/json").content(raw.getBytes(StandardCharsets.UTF_8)).header("X-SePay-Timestamp",time).header("X-SePay-Signature",signature(raw,time))).andExpect(status().isOk()).andExpect(content().json("{\"success\":true}"));}
    @Test void sampleIsAcknowledgedWithoutSessionCsrfMoneyOrOrderChanges()throws Exception{
        int money=db.queryForObject("select count(*) from money_transaction",Integer.class),orders=db.queryForObject("select count(*) from rental_order",Integer.class);String reference=ref();
        send(payload(0,reference,10000));
        assertThat(db.queryForObject("select status from sepay_webhook_event where reference_code=?",String.class,reference)).isEqualTo("TEST");
        assertThat(db.queryForObject("select count(*) from money_transaction",Integer.class)).isEqualTo(money);
        assertThat(db.queryForObject("select count(*) from rental_order",Integer.class)).isEqualTo(orders);
    }
    @Test void missingWrongTamperedStaleAndFutureSignaturesAreRejected()throws Exception{
        String body=payload(id(),ref(),10000),time=timestamp();int before=db.queryForObject("select count(*) from sepay_webhook_event",Integer.class);
        mvc.perform(post(PATH).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType("application/json").content(body).header("X-SePay-Timestamp",time).header("X-SePay-Signature","sha256="+"0".repeat(64))).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType("application/json").content(body.replace("10000","10001")).header("X-SePay-Timestamp",time).header("X-SePay-Signature",signature(body,time))).andExpect(status().isUnauthorized());
        for(long delta:new long[]{-301,301}){String invalid=String.valueOf(Instant.now().getEpochSecond()+delta);mvc.perform(post(PATH).contentType("application/json").content(body).header("X-SePay-Timestamp",invalid).header("X-SePay-Signature",signature(body,invalid))).andExpect(status().isUnauthorized());}
        assertThat(db.queryForObject("select count(*) from sepay_webhook_event",Integer.class)).isEqualTo(before);
    }
    @Test void rawBytesAreVerifiedAndFormattingDoesNotCreateAnotherReceipt()throws Exception{
        String reference=ref(),body=payload(id(),reference,10000);send(body);String formatted="\n "+body+" \n";String time=timestamp();
        mvc.perform(post(PATH).contentType("application/json").content(formatted).header("X-SePay-Timestamp",time).header("X-SePay-Signature",signature(body,time))).andExpect(status().isUnauthorized());
        send(formatted);
        assertThat(db.queryForObject("select count(*) from sepay_webhook_event where reference_code=?",Integer.class,reference)).isEqualTo(1);
        assertThat(db.queryForObject("select delivery_count from sepay_webhook_event where reference_code=?",Integer.class,reference)).isEqualTo(2);
        assertThat(db.queryForObject("select status from sepay_webhook_event where reference_code=?",String.class,reference)).isEqualTo("RECEIVED");
    }
    @Test void correctlySignedInvalidJsonAndInvalidTransferFieldsAreRejected()throws Exception{
        String base=payload(id(),ref(),10000);
        for(String raw:List.of("[]","{invalid",base.replace("\"id\":","\"id\":0,\"id\":"),base.replace("10000","1.5"),base.replace("\"in\"","\"bad\""),base.replace("2026-11-06","2026-02-30"))){String time=timestamp();mvc.perform(post(PATH).contentType("application/json").content(raw).header("X-SePay-Timestamp",time).header("X-SePay-Signature",signature(raw,time))).andExpect(status().isBadRequest());}
    }
    @Test void concurrentRetriesCreateOneReceiptAndNeverPostToLedger()throws Exception{
        String reference=ref(),body=payload(id(),reference,10000);int money=db.queryForObject("select count(*) from money_transaction",Integer.class);
        var pool=Executors.newFixedThreadPool(6);var start=new CountDownLatch(1);var futures=new ArrayList<Future<?>>();
        try{for(int n=0;n<6;n++)futures.add(pool.submit(()->{start.await();send(body);return null;}));start.countDown();for(var future:futures)future.get(20,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        assertThat(db.queryForObject("select count(*) from sepay_webhook_event where reference_code=?",Integer.class,reference)).isEqualTo(1);
        assertThat(db.queryForObject("select delivery_count from sepay_webhook_event where reference_code=?",Integer.class,reference)).isEqualTo(6);
        assertThat(db.queryForObject("select count(*) from money_transaction",Integer.class)).isEqualTo(money);
    }
    @Test void conflictingReplayIsStoredForReviewWithoutOverwritingOriginal()throws Exception{
        long id=id();String reference=ref();send(payload(id,reference,10000));send(payload(id,reference,20000));
        assertThat(db.queryForObject("select amount from sepay_webhook_event where transaction_id=? and channel='junit'",Long.class,id)).isEqualTo(10000L);
        assertThat(db.queryForObject("select status from sepay_webhook_event where transaction_id=? and channel='junit'",String.class,id)).isEqualTo("CONFLICT");
        assertThat(db.queryForObject("select count(*) from sepay_webhook_conflict c join sepay_webhook_event e on e.id=c.event_id where e.transaction_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void receiverLimitsBodyAndRejectsUnsupportedMediaType()throws Exception{
        mvc.perform(post(PATH).contentType("application/json").content(new byte[65537])).andExpect(status().isPayloadTooLarge());
        mvc.perform(post(PATH).contentType("text/plain").content("test")).andExpect(status().isUnsupportedMediaType());
    }
    @Test void financeViewsStayPrivateAndExistingCommandsStillRequireCsrf()throws Exception{
        mvc.perform(get("/api/finance/sepay/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/finance/sepay/events").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/sepay/status").with(user("stock").roles("INVENTORY"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/sepay/status").with(user("owner").roles("OWNER"))).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true)).andExpect(jsonPath("$.automaticPosting").value(false)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SECRET))));
        mvc.perform(post("/api/finance/receive").with(user("owner").roles("OWNER")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(PATH+"/extra").contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void disabledConfigurationFailsClosed(){
        var auth=new SePayHmacAuthenticator(SECRET,false,"junit",Clock.systemUTC());assertThat(auth.ready()).isFalse();
        assertThatThrownBy(()->auth.verify(new byte[]{1},timestamp(),"sha256="+"0".repeat(64))).isInstanceOfSatisfying(DomainException.class,e->assertThat(e.status()).isEqualTo(503));
    }
}
