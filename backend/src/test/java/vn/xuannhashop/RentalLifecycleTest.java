package vn.xuannhashop;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.xuannhashop.business.rental.application.*;
import vn.xuannhashop.business.rental.domain.RentalOrder;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.maintenance.application.MaintenanceApplication;
import vn.xuannhashop.business.finance.application.FinanceApplication;
import vn.xuannhashop.business.identity.application.IdentityApplication;
import vn.xuannhashop.business.delivery.application.DeliveryApplication;
import vn.xuannhashop.business.reservation.application.ReservationApplication;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.infrastructure.config.DevSeed;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.jobs-enabled=false"})
class RentalLifecycleTest {
    @Autowired RentalApplication rentals;@Autowired RentalChangeApplication changes;
    @Autowired RentalIncidentApplication incidents;@Autowired RentalMonitoringApplication monitoring;
    @Autowired FinanceApplication finance;@Autowired EquipmentApplication equipment;
    @Autowired MaintenanceApplication maintenance;@Autowired DeliveryApplication delivery;
    @Autowired ReservationApplication schedules;@Autowired IdentityApplication identity;@Autowired JdbcTemplate db;
    static String key(){return UUID.randomUUID().toString();}
    <T>T as(String user,Supplier<T> action){var previous=SecurityContextHolder.getContext();var next=SecurityContextHolder.createEmptyContext();next.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,List.of()));SecurityContextHolder.setContext(next);try{return action.get();}finally{SecurityContextHolder.setContext(previous);}}
    void act(String user,Runnable action){as(user,()->{action.run();return null;});}
    record Fixture(UUID type,List<UUID> assets) {}
    Fixture fixture(int count){
        UUID type=UUID.randomUUID();String code="T-"+key().substring(0,8).toUpperCase(Locale.ROOT);
        db.update("insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values(?,?,'Thiết bị kiểm thử vòng thuê','Test','Test','Test','fan',100,200)",type,code);
        var assets=new ArrayList<UUID>();for(int n=0;n<count;n++){final int i=n;UUID id=as("owner",()->equipment.addAsset(type,code+"-"+i));act("technician",()->maintenance.inspect(id,true,true,true,"Fixture đã kiểm tra",key()));assets.add(id);}return new Fixture(type,assets);
    }
    UUID hold(Fixture f,Instant from,int days){return as("owner",()->rentals.hold(new RentalApplication.Create(DevSeed.id("khach-an"),List.of(new RentalApplication.Line(f.type(),1)),from,from.plusSeconds(days*86400L),"Địa chỉ thỏa thuận ban đầu",true),key()));}
    UUID hold(Fixture f){return hold(f,Instant.now().plusSeconds(600),1);}
    RentalOrder order(UUID id){return as("owner",()->(RentalOrder)rentals.detail(id).get("order"));}
    @SuppressWarnings("unchecked") Map<String,Object> line(UUID id){return ((List<Map<String,Object>>)as("owner",()->rentals.detail(id)).get("items")).get(0);}
    void confirm(UUID id){act("accountant",()->finance.receive(id,"DEPOSIT_IN",order(id).depositRequired(),"Cọc fixture",key()));act("stock",()->rentals.confirm(id,key()));}
    void handover(UUID id){act("delivery",()->delivery.handover(id,List.of((UUID)line(id).get("id")),"Khách fixture","Biên bản fixture đầy đủ",key()));}
    RentalChangeApplication.Request request(UUID id,String kind,Instant to){var o=order(id);return new RentalChangeApplication.Request(kind,o.version(),o.startsAt(),to,"Địa chỉ mới đã thỏa thuận",List.of(),"Kiểm thử thay đổi có xác nhận",true);}
    UUID extend(UUID id){return as("customer",()->changes.propose(id,request(id,"EXTEND",order(id).endsAt().plusSeconds(86400)),key()));}

    @Test void customerExtensionUsesFrozenRatesAndKeepsImmutableAgreement(){
        var f=fixture(1);UUID id=hold(f);confirm(id);UUID proposal=extend(id);
        db.update("update equipment_type set daily_rate=999 where id=?",f.type());
        String operation=key();act("owner",()->changes.decide(id,proposal,true,"Khách và cửa hàng đồng ý",operation));act("owner",()->changes.decide(id,proposal,true,"Khách và cửa hàng đồng ý",operation));
        assertThat(order(id).rentalTotal()).isEqualByComparingTo("200");
        assertThat(db.queryForObject("select count(*) from agreement_revision where order_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(db.queryForObject("select snapshot->>'rentalTotal' from agreement_revision where order_id=? and revision=1",String.class,id)).isEqualTo("100.00");
        assertThat(db.queryForObject("select count(*) from reservation where order_id=? and status='ALLOCATED'",Integer.class,id)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from reservation where order_id=? and status='RELEASED'",Integer.class,id)).isEqualTo(1);
    }
    @Test void staffProposalRequiresCustomerAcceptanceAndDoesNotRewriteOriginalAddress(){
        UUID id=hold(fixture(1));UUID proposal=as("stock",()->changes.propose(id,request(id,"ADDRESS",order(id).endsAt()),key()));
        assertThatThrownBy(()->act("owner",()->changes.decide(id,proposal,true,"Chưa khách xác nhận",key()))).isInstanceOf(DomainException.class);
        act("customer",()->changes.accept(id,proposal,key()));act("owner",()->changes.decide(id,proposal,true,"Khách chấp thuận trực tuyến",key()));
        assertThat(order(id).address()).isEqualTo("Địa chỉ mới đã thỏa thuận");
        assertThat(db.queryForObject("select snapshot->>'address' from agreement_revision where order_id=? and revision=1",String.class,id)).isEqualTo("Địa chỉ thỏa thuận ban đầu");
    }
    @Test void competingBookingAfterProposalMakesApprovalRollbackWithoutChangingOldSchedule(){
        var f=fixture(1);UUID id=hold(f);confirm(id);UUID proposal=extend(id);var before=order(id);
        hold(f,before.endsAt().plusSeconds(3601),1);
        assertThatThrownBy(()->act("owner",()->changes.decide(id,proposal,true,"Duyệt lại lịch",key()))).isInstanceOf(DomainException.class);
        assertThat(order(id).endsAt()).isEqualTo(before.endsAt());
        assertThat(db.queryForObject("select status from rental_change where id=?",String.class,proposal)).isEqualTo("PENDING");
        assertThat(db.queryForObject("select count(*) from agreement_revision where order_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void staleQuoteCannotOverwriteAChangedOrder(){
        UUID id=hold(fixture(1));confirm(id);UUID proposal=as("customer",()->changes.propose(id,request(id,"ADDRESS",order(id).endsAt()),key()));
        act("stock",()->rentals.prepare(id,key()));
        assertThatThrownBy(()->act("owner",()->changes.decide(id,proposal,true,"Báo giá cũ",key()))).isInstanceOf(DomainException.class);
        assertThat(order(id).address()).isEqualTo("Địa chỉ thỏa thuận ban đầu");
        act("owner",()->rentals.cancel(id,key()));
        assertThat(db.queryForObject("select status from rental_change where id=?",String.class,proposal)).isEqualTo("CANCELLED");
    }
    @Test void replacingAPreparedDeviceReleasesOldAssetAndRequiresPreparationAgain(){
        var f=fixture(2);UUID id=hold(f);confirm(id);act("stock",()->rentals.prepare(id,key()));var old=line(id);
        UUID target=f.assets().stream().filter(x->!x.equals(old.get("equipment_id"))).findFirst().orElseThrow();
        var req=new RentalChangeApplication.Request("SWAP",order(id).version(),null,null,null,List.of(new RentalChangeApplication.Swap((UUID)old.get("id"),target)),"Mã thay thế cùng loại đã kiểm tra",true);
        UUID proposal=as("owner",()->changes.propose(id,req,key()));act("customer",()->changes.accept(id,proposal,key()));act("owner",()->changes.decide(id,proposal,true,"Đã đối chiếu mã thay thế",key()));
        assertThat(line(id).get("equipment_id")).isEqualTo(target);assertThat(order(id).status()).isEqualTo("CONFIRMED");
        assertThat(equipment.asset((UUID)old.get("equipment_id")).state()).isEqualTo("READY");
    }
    @Test void simultaneousExtensionAndCompetingHoldNeverProduceOverlappingActiveSchedules()throws Exception{
        var f=fixture(1);UUID id=hold(f);confirm(id);UUID proposal=extend(id);var original=order(id);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            Future<Boolean> a=pool.submit(()->{start.await();try{act("owner",()->changes.decide(id,proposal,true,"Duyệt đồng thời",key()));return true;}catch(DomainException ex){return false;}});
            Future<Boolean> b=pool.submit(()->{start.await();try{hold(f,original.endsAt().plusSeconds(3601),1);return true;}catch(DomainException ex){return false;}});
            start.countDown();assertThat((a.get(20,TimeUnit.SECONDS)?1:0)+(b.get(20,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
            assertThat(db.queryForObject("select count(*) from reservation a join reservation b on a.equipment_id=b.equipment_id and a.id<b.id and a.occupied_from<b.occupied_until and a.occupied_until>b.occupied_from where a.equipment_id=? and a.status<>'RELEASED' and b.status<>'RELEASED'",Integer.class,f.assets().get(0))).isZero();
        }finally{pool.shutdownNow();}
    }
    @Test void overdueAssetBlocksFutureConfirmationAndMonitoringDeduplicatesNotices(){
        var f=fixture(1);UUID id=hold(f);confirm(id);handover(id);UUID future=hold(f,order(id).endsAt().plusSeconds(3601),1);
        Timestamp past=Timestamp.from(Instant.now().minusSeconds(60));db.update("update rental_order set starts_at=?,ends_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(172800)),past,id);
        assertThat(as("owner",()->monitoring.attention())).anyMatch(row->row.get("order_id").equals(future)&&row.get("risk").equals("LATE_PREVIOUS"));
        assertThat(schedules.available(f.type(),Instant.now().plusSeconds(10*86400L),Instant.now().plusSeconds(11*86400L))).isZero();
        act("accountant",()->finance.receive(future,"DEPOSIT_IN",order(future).depositRequired(),"Cọc đơn bị ảnh hưởng",key()));
        assertThatThrownBy(()->act("stock",()->rentals.confirm(future,key()))).isInstanceOf(DomainException.class);
        monitoring.monitor();monitoring.monitor();
        assertThat(db.queryForObject("select count(*) from notification where order_id=? and event_type='OVERDUE_NOTICE'",Integer.class,id)).isEqualTo(1);
        assertThat(as("customer",()->monitoring.notices(future))).noneMatch(row->row.get("recipient_scope").equals("STAFF"));
    }
    @Test void lostItemIsNeverRecordedAsReturnedAndApprovedChargeMustBePaidBeforeRefund(){
        String manager="mgr_"+key().substring(0,8);
        db.update("insert into user_account(id,username,password_hash,display_name,role,enabled) values(?,?,'test-only','Quản lý kiểm thử','MANAGER',true)",UUID.randomUUID(),manager);
        assertThatThrownBy(()->act(manager,()->finance.decide(UUID.randomUUID(),true,key()))).isInstanceOfSatisfying(DomainException.class,ex->assertThat(ex.status()).isEqualTo(403));
        assertThatThrownBy(()->as(manager,()->finance.approvals())).isInstanceOfSatisfying(DomainException.class,ex->assertThat(ex.status()).isEqualTo(403));

        UUID id=hold(fixture(1));confirm(id);handover(id);var item=line(id);
        UUID incident=as("owner",()->incidents.report(id,new RentalIncidentApplication.Report((UUID)item.get("id"),"LOST",new BigDecimal("300"),"Thiết bị không thể thu hồi","Khách fixture","Biên bản đối chiếu có xác nhận và mức bồi thường"),key()));
        assertThatThrownBy(()->act("owner",()->incidents.decide(id,incident,true,"Tự duyệt",key()))).isInstanceOf(DomainException.class);
        act("accountant",()->incidents.decide(id,incident,true,"Đã kiểm tra căn cứ xử lý",key()));
        assertThat(line(id).get("returned_at")).isNull();assertThat(line(id).get("resolution_kind")).isEqualTo("LOST");
        assertThat(equipment.asset((UUID)item.get("equipment_id")).state()).isEqualTo("SUSPENDED");
        assertThatThrownBy(()->as("owner",()->finance.requestRefund(id,new BigDecimal("200"),"Chưa thu bồi thường",key()))).isInstanceOf(DomainException.class);
        act("accountant",()->{finance.receive(id,"RENTAL_IN",new BigDecimal("100"),"Tiền thuê",key());finance.receive(id,"CHARGE_IN",new BigDecimal("300"),"Tiền bồi thường đã nhận",key());});
        UUID refund=as("accountant",()->finance.requestRefund(id,new BigDecimal("200"),"Đã đối chiếu xong nghĩa vụ",key()));act("owner",()->finance.decide(refund,true,key()));act("accountant",()->rentals.settle(id,key()));
        assertThat(order(id).status()).isEqualTo("COMPLETED");
    }
    @Test void damagedItemCanSettleAfterApprovedCompensationWithoutBeingMarkedReady(){
        UUID id=hold(fixture(1));confirm(id);handover(id);var item=line(id);
        act("delivery",()->delivery.returned(id,List.of((UUID)item.get("id")),"Khách fixture","DAMAGED","Đối chiếu ghi nhận hư hỏng",key()));
        act("technician",()->maintenance.inspect((UUID)item.get("equipment_id"),false,true,true,"Hỏng, không cho thuê lại",key()));
        UUID incident=as("delivery",()->incidents.report(id,new RentalIncidentApplication.Report((UUID)item.get("id"),"DAMAGE",new BigDecimal("50"),"Hư hỏng đã đối chiếu","Khách fixture","Biên bản đồng ý mức bồi thường 50"),key()));
        act("accountant",()->incidents.decide(id,incident,true,"Đối chiếu biên bản",key()));
        act("accountant",()->{finance.receive(id,"RENTAL_IN",new BigDecimal("100"),"Tiền thuê",key());finance.receive(id,"CHARGE_IN",new BigDecimal("50"),"Bồi thường",key());});
        UUID refund=as("accountant",()->finance.requestRefund(id,new BigDecimal("200"),"Đã thu đủ nghĩa vụ",key()));act("owner",()->finance.decide(refund,true,key()));act("accountant",()->rentals.settle(id,key()));
        assertThat(equipment.asset((UUID)item.get("equipment_id")).state()).isEqualTo("MAINTENANCE");
    }
    @Test void pendingLostReportBlocksFutureBookingsUntilAReviewerRejectsIt(){
        var f=fixture(1);UUID id=hold(f);confirm(id);handover(id);
        UUID incident=as("owner",()->incidents.report(id,new RentalIncidentApplication.Report((UUID)line(id).get("id"),"LOST",BigDecimal.ZERO,"Báo không tìm thấy","Khách fixture","Cần kiểm tra lại trước khi kết luận"),key()));
        Instant next=order(id).endsAt().plusSeconds(3601);
        assertThatThrownBy(()->hold(f,next,1)).isInstanceOf(DomainException.class);
        act("accountant",()->incidents.decide(id,incident,false,"Đã tìm thấy, chưa mất",key()));
        assertThat(hold(f,next,1)).isNotNull();
    }
    @Test void contactUpdateIsScopedVersionedAndKeepsAcceptedDeliveryAddress(){
        UUID id=hold(fixture(1));var previous=as("customer",()->identity.profile());
        try{
            var next=new IdentityApplication.Profile(previous.name(),previous.phone(),"profile@example.test","Liên hệ mới, không sửa đơn cũ",previous.version());
            act("customer",()->identity.updateProfile(next));assertThat(order(id).address()).isEqualTo("Địa chỉ thỏa thuận ban đầu");
            assertThatThrownBy(()->act("customer",()->identity.updateProfile(next))).isInstanceOf(DomainException.class);
            assertThatThrownBy(()->as("stock",()->identity.profile())).isInstanceOf(DomainException.class);
        }finally{var current=as("customer",()->identity.profile());act("customer",()->identity.updateProfile(new IdentityApplication.Profile(previous.name(),previous.phone(),previous.email(),previous.address(),current.version())));}
    }
    @Test void failureWhileRecordingCompensationRollsBackLostStateAndScheduleRelease(){
        UUID id=hold(fixture(1));confirm(id);handover(id);var before=line(id);
        UUID incident=as("owner",()->incidents.report(id,new RentalIncidentApplication.Report((UUID)before.get("id"),"LOST",new BigDecimal("300"),"Mất fixture","Khách fixture","Biên bản xử lý"),key()));
        db.execute("create function test_fail_charge() returns trigger language plpgsql as $$ begin if NEW.incident_id='"+incident+"'::uuid then raise exception 'test compensation storage failure' using errcode='23514'; end if; return NEW; end $$");
        db.execute("create trigger test_fail_charge before insert on rental_charge for each row execute function test_fail_charge()");
        try{
            assertThatThrownBy(()->act("accountant",()->incidents.decide(id,incident,true,"Fault injection",key()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(line(id).get("resolved_at")).isNull();assertThat(equipment.asset((UUID)before.get("equipment_id")).state()).isEqualTo("RENTED");
            assertThat(db.queryForObject("select status from rental_incident where id=?",String.class,incident)).isEqualTo("PENDING");
            assertThat(db.queryForObject("select count(*) from reservation where order_id=? and status='ALLOCATED'",Integer.class,id)).isEqualTo(1);
        }finally{db.execute("drop trigger test_fail_charge on rental_charge");db.execute("drop function test_fail_charge()");}
    }
    @Test void damageResolutionCannotBeApprovedBeforePostReturnInspection(){
        UUID id=hold(fixture(1));confirm(id);handover(id);UUID itemId=(UUID)line(id).get("id");
        act("delivery",()->delivery.returned(id,List.of(itemId),"Khách fixture","DAMAGED","Biên bản thu hồi có hư hỏng",key()));
        UUID incident=as("owner",()->incidents.report(id,new RentalIncidentApplication.Report(itemId,"DAMAGE",BigDecimal.ZERO,"Xác minh hao mòn","Khách fixture","Đề nghị không thu thêm, chờ đánh giá kỹ thuật"),key()));
        assertThatThrownBy(()->act("accountant",()->incidents.decide(id,incident,true,"Chưa kiểm tra",key()))).isInstanceOf(DomainException.class);
        assertThat(db.queryForObject("select count(*) from rental_charge where incident_id=?",Integer.class,incident)).isZero();
    }
}
