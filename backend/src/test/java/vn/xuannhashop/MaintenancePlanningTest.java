package vn.xuannhashop;

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
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.maintenance.application.*;
import vn.xuannhashop.business.reservation.application.ReservationApplication;
import vn.xuannhashop.business.rental.application.RentalApplication;
import vn.xuannhashop.business.shared.DomainException;
import vn.xuannhashop.infrastructure.config.DevSeed;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.jobs-enabled=false"})
class MaintenancePlanningTest {
    @Autowired MaintenancePlanningApplication plans;@Autowired MaintenanceApplication maintenance;
    @Autowired ReservationApplication reservations;@Autowired RentalApplication rentals;
    @Autowired EquipmentApplication inventory;@Autowired JdbcTemplate db;
    String key(){return UUID.randomUUID().toString();}
    <T>T as(String user,Supplier<T> action){var old=SecurityContextHolder.getContext();var current=SecurityContextHolder.createEmptyContext();current.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,List.of()));SecurityContextHolder.setContext(current);try{return action.get();}finally{SecurityContextHolder.setContext(old);}}
    void act(String user,Runnable action){as(user,()->{action.run();return null;});}
    record Fixture(UUID type,UUID equipment,Instant from,Instant to) {}
    Fixture fixture(){
        UUID type=UUID.randomUUID();String code="M-"+key().substring(0,8).toUpperCase(Locale.ROOT);
        db.update("insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values(?,?,'JUNIT plan','Test','Test','Test','fan',100,200)",type,code);
        UUID equipment=as("owner",()->inventory.addAsset(type,code+"-001"));act("technician",()->maintenance.inspect(equipment,true,true,true,"JUNIT accepted",key()));Instant from=Instant.now().plusSeconds(86400);return new Fixture(type,equipment,from,from.plusSeconds(7200));
    }
    UUID schedule(Fixture f){return as("technician",()->plans.schedule(f.equipment(),f.from(),f.to(),"JUNIT bảo trì dự kiến",key()));}
    UUID hold(Fixture f){return as("owner",()->rentals.hold(new RentalApplication.Create(DevSeed.id("khach-an"),List.of(new RentalApplication.Line(f.type(),1)),f.from(),f.to(),"JUNIT bảo trì tranh lịch",true),key()));}
    String state(Fixture f){return db.queryForObject("select state from equipment where id=?",String.class,f.equipment());}
    void due(UUID plan){db.update("update maintenance_plan set starts_at=now()-interval '1 second' where id=?",plan);db.update("update maintenance_window set occupied_from=now()-interval '1 second' where id=?",plan);}

    @Test void plannedWindowBlocksRentalIncludingPreparationBufferButNotSeparateDates(){
        var f=fixture();UUID plan=schedule(f);assertThat(reservations.available(f.type(),f.from(),f.to())).isZero();
        assertThat(reservations.available(f.type(),f.from().minusSeconds(3600),f.from().minusSeconds(60))).isZero();
        assertThat(reservations.available(f.type(),f.to().plusSeconds(3600),f.to().plusSeconds(7200))).isEqualTo(1);
        assertThatThrownBy(()->hold(f)).isInstanceOf(DomainException.class);assertThat(state(f)).isEqualTo("READY");
        act("technician",()->plans.cancel(plan,"Đổi kế hoạch sau đối chiếu",key()));assertThat(reservations.available(f.type(),f.from(),f.to())).isEqualTo(1);
    }
    @Test void heldRentalPreventsInsertingMaintenanceAndNothingIsOverwritten(){
        var f=fixture();UUID order=hold(f);assertThatThrownBy(()->schedule(f)).isInstanceOf(DomainException.class);
        assertThat(db.queryForObject("select count(*) from maintenance_window where equipment_id=?",Integer.class,f.equipment())).isZero();assertThat(db.queryForObject("select count(*) from reservation where order_id=? and status='HELD'",Integer.class,order)).isEqualTo(1);
    }
    @Test void bookingAndMaintenanceRaceUsesSameDatabaseEquipmentLock()throws Exception{
        var f=fixture();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{Future<Boolean> a=pool.submit(()->{gate.await();try{hold(f);return true;}catch(DomainException ex){return false;}}),b=pool.submit(()->{gate.await();try{schedule(f);return true;}catch(DomainException ex){return false;}});gate.countDown();assertThat((a.get(20,TimeUnit.SECONDS)?1:0)+(b.get(20,TimeUnit.SECONDS)?1:0)).isEqualTo(1);}finally{pool.shutdownNow();}
    }
    @Test void overdueUnstartedPlanBlocksFutureUntilExplicitCancellation(){
        var f=fixture();UUID p=schedule(f);db.update("update maintenance_plan set starts_at=now()-interval '2 hours',ends_at=now()-interval '1 hour' where id=?",p);db.update("update maintenance_window set occupied_from=now()-interval '2 hours',occupied_until=now()-interval '1 hour' where id=?",p);
        assertThat(reservations.available(f.type(),f.from(),f.to())).isZero();act("owner",()->plans.cancel(p,"Không thực hiện, đã đánh giá lại",key()));assertThat(reservations.available(f.type(),f.from(),f.to())).isEqualTo(1);
    }
    @Test void startAndCompleteRequireRealInspectionAndFailedOutcomeStaysUnavailable(){
        var f=fixture();UUID p=schedule(f);assertThatThrownBy(()->act("technician",()->plans.start(p,key()))).isInstanceOf(DomainException.class);due(p);act("technician",()->plans.start(p,key()));assertThat(state(f)).isEqualTo("MAINTENANCE");
        String operation=key();act("technician",()->plans.complete(p,false,true,true,"Chưa đạt an toàn, cần sửa tiếp",operation));act("technician",()->plans.complete(p,false,true,true,"Chưa đạt an toàn, cần sửa tiếp",operation));
        assertThat(state(f)).isEqualTo("MAINTENANCE");assertThat(db.queryForObject("select active from maintenance_window where id=?",Boolean.class,p)).isFalse();assertThat(reservations.available(f.type(),f.from(),f.to())).isZero();assertThat(db.queryForObject("select count(*) from inspection where equipment_id=?",Integer.class,f.equipment())).isEqualTo(2);
        act("technician",()->maintenance.inspect(f.equipment(),true,true,true,"Đã sửa và kiểm tra đạt",key()));assertThat(reservations.available(f.type(),f.from(),f.to())).isEqualTo(1);
    }
    @Test void successfulMaintenanceReleasesOnlyItsWindowAndHasAuditAndInspection(){
        var f=fixture();UUID p=schedule(f);due(p);act("owner",()->plans.start(p,key()));act("technician",()->plans.complete(p,true,true,true,"Thực hiện đủ và kiểm tra đạt",key()));assertThat(state(f)).isEqualTo("READY");
        assertThat(db.queryForObject("select i.passed from maintenance_plan p join inspection i on i.id=p.inspection_id where p.id=?",Boolean.class,p)).isTrue();assertThat(db.queryForObject("select count(*) from audit_entry where action='MAINTENANCE_PLAN_COMPLETED' and target_id=?",Integer.class,f.equipment())).isEqualTo(1);
    }
    @Test void unauthorizedSchedulingAndFailureAfterWindowInsertDoNotLeaveBlocks(){
        var f=fixture();assertThatThrownBy(()->as("stock",()->plans.schedule(f.equipment(),f.from(),f.to(),"Không có quyền",key()))).isInstanceOf(DomainException.class);
        db.execute("create function fail_plan_junit() returns trigger language plpgsql as $$ begin raise exception 'junit plan failure'; end $$");db.execute("create trigger fail_plan_junit before insert on maintenance_plan for each row execute function fail_plan_junit()");
        try{assertThatThrownBy(()->schedule(f)).isInstanceOf(Exception.class);assertThat(db.queryForObject("select count(*) from maintenance_window where equipment_id=?",Integer.class,f.equipment())).isZero();}finally{db.execute("drop trigger fail_plan_junit on maintenance_plan");db.execute("drop function fail_plan_junit()");}
        assertThat(reservations.available(f.type(),f.from(),f.to())).isEqualTo(1);
    }
}
