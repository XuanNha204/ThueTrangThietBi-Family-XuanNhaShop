package vn.xuannhashop;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.pricing.application.ProductPriceApplication;
import vn.xuannhashop.business.rental.application.*;
import vn.xuannhashop.business.rental.domain.RentalOrder;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.infrastructure.config.DevSeed;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.jobs-enabled=false"})
@AutoConfigureMockMvc
class ProductPriceTest {
    @Autowired ProductPriceApplication prices;@Autowired EquipmentApplication equipment;
    @Autowired RentalApplication rentals;@Autowired RentalChangeApplication changes;
    @Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    String key(){return UUID.randomUUID().toString();}
    <T>T as(String user,Supplier<T> action){var old=SecurityContextHolder.getContext();var current=SecurityContextHolder.createEmptyContext();current.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,List.of()));SecurityContextHolder.setContext(current);try{return action.get();}finally{SecurityContextHolder.setContext(old);}}
    UUID fixture(){UUID type=UUID.randomUUID();String code="P-"+key().substring(0,8);
        db.update("insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values(?,?,'JUNIT price','Test','Test','Test','fan',100,200)",type,code);
        db.update("insert into equipment(id,code,type_id,warehouse_id,state) select ?,?,?,id,'READY' from warehouse limit 1",UUID.randomUUID(),code+"-001",type);return type;}
    ProductPriceApplication.Change change(long version,String daily,String deposit){return new ProductPriceApplication.Change(version,new BigDecimal(daily),new BigDecimal(deposit),"JUNIT điều chỉnh có căn cứ");}
    UUID save(UUID type,ProductPriceApplication.Change c,String key){return as("owner",()->prices.change(type,c,key));}
    RentalApplication.Create input(UUID type,Long version,int day){Instant start=Instant.now().plusSeconds(day*86400L);return new RentalApplication.Create(DevSeed.id("khach-an"),List.of(new RentalApplication.Line(type,1,version)),start,start.plusSeconds(86400),"JUNIT kiểm thử giá",true);}
    UUID hold(RentalApplication.Create c){return as("owner",()->rentals.hold(c,key()));}
    RentalOrder order(UUID id){return as("owner",()->(RentalOrder)rentals.detail(id).get("order"));}
    @Test void updateIsAtomicAuditedAndIdempotentWithZeroDepositAllowed(){
        UUID type=fixture();var c=change(0,"150","0");String key=key();UUID result=save(type,c,key);
        assertThat(save(type,c,key)).isEqualTo(result);var current=equipment.type(type);
        assertThat(current.dailyRate()).isEqualByComparingTo("150");assertThat(current.deposit()).isZero();assertThat(current.priceVersion()).isEqualTo(1);
        var history=as("owner",()->prices.history(type));assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsEntry("old_daily_rate",new BigDecimal("100.00")).containsEntry("old_deposit",new BigDecimal("200.00")).containsEntry("changed_by","owner");
        assertThat(db.queryForObject("select count(*) from audit_entry where target_id=? and action='PRODUCT_PRICE_CHANGED'",Integer.class,type)).isEqualTo(1);
        assertThatThrownBy(()->save(type,change(1,"160","0"),key)).isInstanceOf(DomainException.class);
    }
    @Test void invalidMoneyReasonMissingVersionAndNoChangeNeverWrite(){
        UUID type=fixture();var invalid=new ArrayList<ProductPriceApplication.Change>();
        for(String daily:List.of("0","-1","1.5","1000000000000"))invalid.add(change(0,daily,"200"));
        for(String deposit:List.of("-1","0.01","1000000000000"))invalid.add(change(0,"150",deposit));
        invalid.add(new ProductPriceApplication.Change(null,BigDecimal.ONE,BigDecimal.ZERO,"reason"));
        invalid.add(new ProductPriceApplication.Change(0L,null,BigDecimal.ZERO,"reason"));
        invalid.add(new ProductPriceApplication.Change(0L,BigDecimal.ONE,null,"reason"));
        invalid.add(new ProductPriceApplication.Change(0L,BigDecimal.ONE,BigDecimal.ZERO," "));
        invalid.add(new ProductPriceApplication.Change(0L,BigDecimal.ONE,BigDecimal.ZERO,"x".repeat(501)));
        invalid.add(change(0,"100","200"));
        for(var c:invalid)assertThatThrownBy(()->save(type,c,key())).isInstanceOf(DomainException.class);
        assertThat(equipment.type(type).priceVersion()).isZero();assertThat(as("owner",()->prices.history(type))).isEmpty();
    }
    @Test void endpointRequiresOwnerCsrfAndHistoryIsPrivate()throws Exception{
        UUID type=fixture();String path="/api/catalog/"+type+"/price",body=json.writeValueAsString(change(0,"150","300"));
        for(String actor:List.of("customer","stock","accountant","technician"))mvc.perform(post(path).with(user(actor)).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("owner")).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(get(path+"/history").with(user("customer"))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("owner")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(get(path+"/history").with(user("stock"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].daily_rate").value(150));
        mvc.perform(post(path).with(user("owner")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/catalog/"+UUID.randomUUID()+"/price").with(user("owner")).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isNotFound());
    }
    @Test void simultaneousEditsHaveOneWinnerAndDoNotOverwrite()throws Exception{
        UUID type=fixture();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{var futures=new ArrayList<Future<Boolean>>();for(String amount:List.of("150","175"))futures.add(pool.submit(()->{gate.await();try{save(type,change(0,amount,"300"),key());return true;}catch(DomainException e){return false;}}));gate.countDown();int wins=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS))wins++;assertThat(wins).isEqualTo(1);assertThat(as("owner",()->prices.history(type))).hasSize(1);}finally{pool.shutdownNow();}
    }
    @Test void existingOrderAndExtensionKeepSnapshotWhileNewOrdersUseNewPrice(){
        UUID type=fixture(),old=hold(input(type,0L,2));var before=order(old);String snapshot=db.queryForObject("select snapshot::text from agreement_revision where order_id=? order by revision limit 1",String.class,old);
        save(type,change(0,"150","300"),key());assertThat(order(old)).isEqualTo(before);
        assertThat(db.queryForObject("select daily_rate from rental_item where order_id=?",BigDecimal.class,old)).isEqualByComparingTo("100");
        assertThat(db.queryForObject("select snapshot::text from agreement_revision where order_id=? order by revision limit 1",String.class,old)).isEqualTo(snapshot);
        var extension=as("owner",()->changes.quote(old,new RentalChangeApplication.Request("EXTEND",before.version(),before.startsAt(),before.endsAt().plusSeconds(86400),before.address(),List.of(),"JUNIT gia hạn theo giá đã chốt",true)));
        assertThat(extension.rentalTotal()).isEqualByComparingTo("200");assertThat(extension.depositRequired()).isEqualByComparingTo("200");
        var fresh=order(hold(input(type,1L,5)));assertThat(fresh.rentalTotal()).isEqualByComparingTo("150");assertThat(fresh.depositRequired()).isEqualByComparingTo("300");
    }
    @Test void staleQuoteIsRejectedBeforeCreatingAnyOrderOrReservation(){
        UUID type=fixture();var input=input(type,0L,2);var quote=rentals.quote(input);
        assertThat(((Map<?,?>)((List<?>)quote.get("lines")).get(0)).get("priceVersion")).isEqualTo(0L);
        save(type,change(0,"150","300"),key());assertThatThrownBy(()->hold(input)).isInstanceOf(DomainException.class).hasMessageContaining("báo giá");
        assertThat(db.queryForObject("select count(*) from rental_item i join equipment e on e.id=i.equipment_id where e.type_id=?",Integer.class,type)).isZero();
    }
    @Test void racingPriceEditCannotMixOrderTotalAndItemSnapshot()throws Exception{
        UUID type=fixture();var c=input(type,null,2);var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{Future<UUID> a=pool.submit(()->{gate.await();return hold(c);});Future<UUID> b=pool.submit(()->{gate.await();return save(type,change(0,"150","300"),key());});gate.countDown();UUID id=a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
            var row=db.queryForMap("select daily_rate,rental_amount,deposit_amount from rental_item where order_id=?",id);var o=order(id);
            assertThat(o.rentalTotal()).isEqualByComparingTo((BigDecimal)row.get("rental_amount"));assertThat(o.depositRequired()).isEqualByComparingTo((BigDecimal)row.get("deposit_amount"));
            if(o.rentalTotal().compareTo(new BigDecimal("100"))==0)assertThat(o.depositRequired()).isEqualByComparingTo("200");else{assertThat(o.rentalTotal()).isEqualByComparingTo("150");assertThat(o.depositRequired()).isEqualByComparingTo("300");}
        }finally{pool.shutdownNow();}
    }
    @Test void failedHistoryInsertRollsBackPriceVersionAndAuditAndAllowsRetry(){
        UUID type=fixture();String operation=key();
        db.execute("create function junit_reject_price() returns trigger language plpgsql as $$ begin raise exception 'JUNIT failure'; end $$");
        db.execute("create trigger junit_reject_price before insert on product_price_change for each row execute function junit_reject_price()");
        try{assertThatThrownBy(()->save(type,change(0,"150","300"),operation)).isInstanceOf(RuntimeException.class);assertThat(equipment.type(type).priceVersion()).isZero();assertThat(equipment.type(type).dailyRate()).isEqualByComparingTo("100");assertThat(db.queryForObject("select count(*) from audit_entry where target_id=? and action='PRODUCT_PRICE_CHANGED'",Integer.class,type)).isZero();}
        finally{db.execute("drop trigger junit_reject_price on product_price_change");db.execute("drop function junit_reject_price()");}
        save(type,change(0,"150","300"),operation);assertThat(equipment.type(type).priceVersion()).isEqualTo(1);
    }
}
