package vn.xuannhashop;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import vn.xuannhashop.business.rental.application.RentalApplication;
import vn.xuannhashop.business.rental.domain.RentalOrder;
import vn.xuannhashop.business.finance.application.FinanceApplication;
import vn.xuannhashop.business.delivery.application.DeliveryApplication;
import vn.xuannhashop.business.maintenance.application.MaintenanceApplication;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.identity.application.IdentityApplication;
import vn.xuannhashop.business.migration.application.MigrationApplication;
import vn.xuannhashop.business.payroll.application.PayrollApplication;
import vn.xuannhashop.business.reporting.application.ReportingApplication;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.infrastructure.config.DevSeed;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"app.jobs-enabled=false"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest {
    @Autowired RentalApplication rentals;@Autowired FinanceApplication finance;@Autowired DeliveryApplication delivery;
    @Autowired MaintenanceApplication maintenance;@Autowired EquipmentApplication equipment;@Autowired IdentityApplication identity;
    @Autowired MigrationApplication migration;@Autowired PayrollApplication payroll;@Autowired ReportingApplication reports;
    @Autowired JdbcTemplate db;@Autowired MockMvc mvc;
    static String key(){return UUID.randomUUID().toString();}
    <T>T as(String user,Supplier<T> action){var previous=SecurityContextHolder.getContext();var next=SecurityContextHolder.createEmptyContext();next.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,List.of()));SecurityContextHolder.setContext(next);try{return action.get();}finally{SecurityContextHolder.setContext(previous);}}
    void act(String user,Runnable action){as(user,()->{action.run();return null;});}
    RentalApplication.Create input(String type,int quantity,Instant from){return new RentalApplication.Create(DevSeed.id("khach-an"),List.of(new RentalApplication.Line(DevSeed.id(type),quantity)),from,from.plusSeconds(86400),"Địa chỉ kiểm thử độc lập",true);}
    UUID hold(String type,int qty,int days){return as("owner",()->rentals.hold(input(type,qty,Instant.now().plusSeconds(days*86400L+600)),key()));}
    RentalOrder order(UUID id){return as("owner",()->(RentalOrder)rentals.detail(id).get("order"));}
    @SuppressWarnings("unchecked") List<Map<String,Object>> items(UUID id){return as("owner",()->(List<Map<String,Object>>)rentals.detail(id).get("items"));}
    @Test @Order(1) void fullRentalSupportsPartialReturnInspectionRefundAndSettlement(){
        UUID id=hold("TB08",2,0);var o=order(id);
        act("accountant",()->{finance.receive(id,"DEPOSIT_IN",o.depositRequired(),"Test: đã đối chiếu tiền cọc",key());finance.receive(id,"RENTAL_IN",o.rentalTotal(),"Test: đã đối chiếu tiền thuê",key());});
        act("stock",()->rentals.confirm(id,key()));var ids=items(id).stream().map(i->(UUID)i.get("id")).toList();
        act("delivery",()->delivery.handover(id,ids,"Người nhận kiểm thử","Đủ thiết bị, phụ kiện và kiểm tra thực tế",key()));
        act("delivery",()->delivery.returned(id,List.of(ids.get(0)),"Người trả kiểm thử","GOOD","Trả thiết bị thứ nhất",key()));
        assertThat(order(id).status()).isEqualTo("PARTIAL_RETURN");
        act("delivery",()->delivery.returned(id,List.of(ids.get(1)),"Người trả kiểm thử","GOOD","Trả thiết bị thứ hai",key()));
        assertThat(order(id).status()).isEqualTo("AWAIT_SETTLEMENT");
        assertThatThrownBy(()->as("accountant",()->finance.requestRefund(id,o.depositRequired(),"Chưa kiểm tra",key()))).isInstanceOf(DomainException.class);
        for(var i:items(id))act("technician",()->maintenance.inspect((UUID)i.get("equipment_id"),true,true,true,"Đã kiểm tra thực tế, đủ và an toàn",key()));
        UUID approval=as("accountant",()->finance.requestRefund(id,o.depositRequired(),"Đã trả đủ và kiểm tra đạt",key()));
        assertThatThrownBy(()->act("accountant",()->finance.decide(approval,true,key()))).isInstanceOf(DomainException.class);
        act("owner",()->finance.decide(approval,true,key()));act("accountant",()->rentals.settle(id,key()));
        assertThat(order(id).status()).isEqualTo("COMPLETED");assertThat(finance.balances(id).depositHeld()).isEqualByComparingTo("0");
        assertThat(db.queryForObject("select count(*) from money_transaction where order_id=? and kind='DEPOSIT_REFUND'",Integer.class,id)).isEqualTo(1);
    }
    @Test @Order(2) void concurrentHoldsNeverExceedPhysicalCapacity()throws Exception{
        var input=input("TB05",1,Instant.now().plusSeconds(30*86400L));var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);var futures=new ArrayList<Future<UUID>>();
        try{for(int i=0;i<8;i++)futures.add(pool.submit(()->{start.await();try{return as("owner",()->rentals.hold(input,key()));}catch(DomainException e){assertThat(e.status()).isEqualTo(409);return null;}}));start.countDown();var success=new ArrayList<UUID>();for(var f:futures){var id=f.get(20,TimeUnit.SECONDS);if(id!=null)success.add(id);}assertThat(success).hasSize(3);assertThat(db.queryForObject("select count(distinct equipment_id) from reservation where order_id in (?,?,?)",Integer.class,success.toArray())).isEqualTo(3);}finally{pool.shutdownNow();}
    }
    @Test @Order(3) void concurrentPaymentRetryCreatesOneLedgerEntry()throws Exception{
        UUID id=hold("TB01",1,40);String operation=key();var pool=Executors.newFixedThreadPool(6);var futures=new ArrayList<Future<UUID>>();try{for(int i=0;i<6;i++)futures.add(pool.submit(()->as("accountant",()->finance.receive(id,"DEPOSIT_IN",new BigDecimal("100000"),"Cùng một chứng từ kiểm thử",operation))));var ids=new HashSet<UUID>();for(var f:futures)ids.add(f.get(20,TimeUnit.SECONDS));assertThat(ids).hasSize(1);assertThat(finance.balances(id).depositHeld()).isEqualByComparingTo("100000");assertThatThrownBy(()->as("accountant",()->finance.receive(id,"DEPOSIT_IN",new BigDecimal("200000"),"Cùng một chứng từ kiểm thử",operation))).isInstanceOf(DomainException.class);}finally{pool.shutdownNow();}
    }
    @Test @Order(4) void lateDepositDoesNotResurrectExpiredReservation(){UUID id=hold("TB04",1,50);Timestamp past=Timestamp.from(Instant.now().minusSeconds(60));db.update("update rental_order set expires_at=? where id=?",past,id);db.update("update reservation set expires_at=? where order_id=?",past,id);act("accountant",()->finance.receive(id,"DEPOSIT_IN",new BigDecimal("800000"),"Tiền đến sau hạn giữ chỗ",key()));assertThatThrownBy(()->act("stock",()->rentals.confirm(id,key()))).isInstanceOf(DomainException.class);rentals.expireHolds();assertThat(order(id).status()).isEqualTo("EXPIRED");assertThat(finance.balances(id).depositHeld()).isEqualByComparingTo("800000");assertThat(db.queryForObject("select count(*) from reservation where order_id=? and status='RELEASED'",Integer.class,id)).isEqualTo(1);}
    @Test @Order(5) void databaseFailureRollsBackOrderItemsAgreementAuditOutboxAndReceipt(){
        UUID type=UUID.randomUUID();String code="TEST-"+key().substring(0,8).toUpperCase(Locale.ROOT);db.update("insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values(?,?,'Thiết bị rollback','Test','Test','Test','fan',100,100)",type,code);
        UUID asset=as("owner",()->equipment.addAsset(type,code+"-1"));act("technician",()->maintenance.inspect(asset,true,true,true,"Fixture hợp lệ trước khi gây lỗi DB",key()));
        var tables=List.of("rental_order","rental_item","agreement_revision","reservation","audit_entry","notification","command_receipt");var before=new HashMap<String,Long>();for(var t:tables)before.put(t,db.queryForObject("select count(*) from "+t,Long.class));
        db.execute("create function test_reject_reservation() returns trigger language plpgsql as $$ begin if NEW.equipment_id='"+asset+"'::uuid then raise exception 'test storage failure' using errcode='23514'; end if; return NEW; end $$");db.execute("create trigger test_storage_failure before insert on reservation for each row execute function test_reject_reservation()");
        try{var c=new RentalApplication.Create(DevSeed.id("khach-an"),List.of(new RentalApplication.Line(type,1)),Instant.now().plusSeconds(600),Instant.now().plusSeconds(87000),"Test rollback",true);assertThatThrownBy(()->as("owner",()->rentals.hold(c,key()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);for(var t:tables)assertThat(db.queryForObject("select count(*) from "+t,Long.class)).as(t).isEqualTo(before.get(t));}finally{db.execute("drop trigger test_storage_failure on reservation");db.execute("drop function test_reject_reservation()");}
    }
    @Test @Order(6) void customerScopeAndBackendRolesAreEnforced()throws Exception{
        mvc.perform(get("/api/rentals/"+DevSeed.id("demo-2")).with(user("customer").roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/customers").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/ledger").with(user("stock").roles("INVENTORY"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").with(user("customer").roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/rentals").with(user("owner").roles("OWNER")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test @Order(7) void lockedAccountLosesExistingSessionAccess()throws Exception{
        UUID account=DevSeed.id("user-customer");act("owner",()->identity.enable(account,false));try{mvc.perform(get("/api/auth/me").with(user("customer").roles("CUSTOMER"))).andExpect(status().isUnauthorized());}finally{act("owner",()->identity.enable(account,true));}
    }
    @Test @Order(8) void csvStagesBeforeDifferentPersonApprovesAndCannotBeImportedTwice(){
        byte[] csv=("sourceId,name,phone,address\nTEST-SOURCE-1,Khách Kiểm Thử,0901888999,Địa chỉ dữ liệu test\n").getBytes(StandardCharsets.UTF_8);int before=db.queryForObject("select count(*) from customer",Integer.class);
        UUID batch=as("stock",()->migration.stage(csv,"test.csv","BATCH-TEST-1",key()));assertThat(db.queryForObject("select count(*) from customer",Integer.class)).isEqualTo(before);
        assertThatThrownBy(()->act("stock",()->migration.commit(batch,key()))).isInstanceOf(DomainException.class);
        act("owner",()->migration.commit(batch,key()));assertThat(db.queryForObject("select count(*) from customer",Integer.class)).isEqualTo(before+1);
        assertThatThrownBy(()->act("owner",()->migration.commit(batch,key()))).isInstanceOf(DomainException.class);
        UUID duplicate=as("stock",()->migration.stage(csv,"test.csv","BATCH-TEST-2",key()));assertThat(as("stock",()->migration.detail(duplicate)).toString()).contains("INVALID");
    }
    @Test @Order(9) void failedCsvCommitIsAtomicAfterSourceChanges(){
        byte[] csv=("sourceId,name,phone,address\nSRC-ROLLBACK-1,Khách Một,0901777001,Địa chỉ một\nSRC-ROLLBACK-2,Khách Hai,0901777002,Địa chỉ hai\n").getBytes(StandardCharsets.UTF_8);
        UUID batch=as("stock",()->migration.stage(csv,"atomic.csv","ATOMIC-BATCH",key()));as("owner",()->identity.createCustomer("Khách phát sinh","0901777002","Địa chỉ phát sinh"));int before=db.queryForObject("select count(*) from customer",Integer.class);
        assertThatThrownBy(()->act("owner",()->migration.commit(batch,key()))).isInstanceOf(DomainException.class);assertThat(db.queryForObject("select count(*) from customer",Integer.class)).isEqualTo(before);assertThat(db.queryForObject("select count(*) from import_source_link where source_id like 'SRC-ROLLBACK-%'",Integer.class)).isZero();
    }
    @Test @Order(10) void privateEvidenceHasObjectScopeAndChecksum()throws Exception{
        UUID order=DevSeed.id("demo-2");var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);UUID evidence=as("delivery",()->delivery.upload(order,bytes.toByteArray(),"kiem-tra.png"));assertThat(as("owner",()->delivery.download(evidence)).bytes()).isEqualTo(bytes.toByteArray());assertThatThrownBy(()->as("customer",()->delivery.download(evidence))).isInstanceOf(DomainException.class);assertThatThrownBy(()->as("delivery",()->delivery.upload(order,"NOT_AN_IMAGE".getBytes(StandardCharsets.UTF_8),"fake.jpg"))).isInstanceOf(DomainException.class);
    }
    @Test @Order(11) void payrollUsesApprovedWorkAndPaysOnce(){
        UUID staff=DevSeed.id("staff-kho");LocalDate day=LocalDate.now().minusDays(2);UUID work=as("hr",()->payroll.work(staff,day,new BigDecimal("8"),key()));act("accountant",()->payroll.approveWork(work,key()));UUID pay=as("accountant",()->payroll.draft(staff,YearMonth.from(day).toString(),key()));assertThatThrownBy(()->act("accountant",()->payroll.approve(pay,key()))).isInstanceOf(DomainException.class);act("owner",()->payroll.approve(pay,key()));String token=key();act("accountant",()->payroll.pay(pay,token));act("accountant",()->payroll.pay(pay,token));assertThat(db.queryForObject("select count(*) from money_transaction where payroll_id=?",Integer.class,pay)).isEqualTo(1);assertThatThrownBy(()->act("accountant",()->payroll.pay(pay,key()))).isInstanceOf(DomainException.class);
        @SuppressWarnings("unchecked") var staffViews=(List<Map<String,Object>>)as("hr",()->payroll.overview()).get("staff");assertThat(staffViews).allMatch(r->!r.containsKey("hourly_rate"));
    }
    @Test @Order(12) void dashboardUsesDatabaseTotalsAndSevenDaySeries(){var result=as("owner",()->reports.dashboard());assertThat(((Number)result.get("total_assets")).intValue()).isEqualTo(db.queryForObject("select count(*) from equipment",Integer.class));assertThat((List<?>)result.get("trend")).hasSize(7);assertThat(as("stock",()->reports.dashboard())).doesNotContainKey("deposits_held");}
    @Test @Order(13) void remainingItemsCanBeDeliveredAfterAnEarlierPartialReturn(){
        UUID id=hold("TB08",2,0);var o=order(id);
        act("accountant",()->finance.receive(id,"DEPOSIT_IN",o.depositRequired(),"Kiểm thử giao nhiều đợt",key()));
        act("stock",()->rentals.confirm(id,key()));
        var lines=items(id).stream().map(i->(UUID)i.get("id")).toList();
        act("delivery",()->delivery.handover(id,List.of(lines.get(0)),"Người nhận","Giao đợt một",key()));
        act("delivery",()->delivery.returned(id,List.of(lines.get(0)),"Người trả","GOOD","Trả đợt một trước khi nhận đợt hai",key()));
        assertThat(order(id).status()).isEqualTo("PARTIAL_RETURN");
        act("delivery",()->delivery.handover(id,List.of(lines.get(1)),"Người nhận","Giao thiết bị còn lại",key()));
        assertThat(order(id).status()).isEqualTo("PARTIAL_RETURN");
        act("delivery",()->delivery.returned(id,List.of(lines.get(1)),"Người trả","GOOD","Trả thiết bị còn lại",key()));
        assertThat(order(id).status()).isEqualTo("AWAIT_SETTLEMENT");
    }
}
