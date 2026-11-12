package vn.xuannhashop.business.rental.application;
import vn.xuannhashop.business.shared.security.ActorContext;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.rental.domain.RentalOrder;
import vn.xuannhashop.business.identity.application.*;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.pricing.application.PricingApplication;
import vn.xuannhashop.business.reservation.application.ReservationApplication;
import vn.xuannhashop.business.finance.application.FinanceApplication;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
@Service
public class RentalOrderService implements RentalApplication {
    private final RentalRepository r;private final EquipmentApplication equipment;private final ReservationApplication reservation;
    private final vn.xuannhashop.business.identity.application.CustomerVerificationApplication verification;private final PricingApplication pricing;private final FinanceApplication finance;private final IdentityApplication identity;
    private final ActorContext actor;private final ActivityService audit;private final Commands commands;private final Clock clock;private final int holdMinutes,buffer;
    public RentalOrderService(vn.xuannhashop.business.identity.application.CustomerVerificationApplication verification,RentalRepository r,EquipmentApplication e,ReservationApplication rs,PricingApplication p,FinanceApplication f,IdentityApplication i,ActorContext a,ActivityService audit,Commands c,Clock clock,@Value("${app.hold-minutes}")int hm,@Value("${app.buffer-minutes}")int b){this.verification=verification;this.r=r;equipment=e;reservation=rs;pricing=p;finance=f;identity=i;actor=a;this.audit=audit;commands=c;this.clock=clock;holdMinutes=hm;buffer=b;}
    public Map<String,Object> quote(Create c){
        validate(c);BigDecimal rent=BigDecimal.ZERO,deposit=BigDecimal.ZERO;var lines=new ArrayList<Map<String,Object>>();
        for(var line:c.lines()) {var t=equipment.type(line.typeId());var q=pricing.quote(t.policyVersion(),c.startsAt(),c.endsAt(),t.dailyRate(),t.deposit(),line.quantity());rent=rent.add(q.rental());deposit=deposit.add(q.deposit());lines.add(Map.of("typeId",t.id(),"name",t.name(),"days",q.days(),"quantity",line.quantity(),"rental",q.rental(),"deposit",q.deposit(),"available",reservation.available(t.id(),c.startsAt(),c.endsAt()),"policyVersion",q.version(),"priceVersion",t.priceVersion()));}
        return Map.of("rentalTotal",rent,"depositRequired",deposit,"lines",lines,"holdMinutes",holdMinutes,"bufferMinutes",buffer,"policyVersion","DEV-1");
    }
    private void validate(Create c){
        if(c==null||c.startsAt()==null||c.endsAt()==null||!c.endsAt().isAfter(c.startsAt())||c.startsAt().isBefore(clock.instant().minus(5,ChronoUnit.MINUTES)))throw DomainException.bad("Thời gian thuê phải hợp lệ và chưa qua.");
        if(c.lines()==null||c.lines().isEmpty()||c.lines().size()>10)throw DomainException.bad("Chọn từ 1 đến 10 loại thiết bị.");
        var seen=new HashSet<UUID>();for(var l:c.lines())if(l.typeId()==null||l.quantity()<1||l.quantity()>20||!seen.add(l.typeId()))throw DomainException.bad("Dòng thiết bị trùng hoặc số lượng không hợp lệ.");
    }
    @Transactional public UUID hold(Create c,String key){
        actor.require("OWNER","MANAGER","INVENTORY","CUSTOMER");
        return commands.once(key,"HOLD:"+c,()->{
            validate(c);pricing.lockPrices(c.lines().stream().map(Line::typeId).toList());
            for(var line:c.lines())if(line.priceVersion()!=null&&line.priceVersion()!=equipment.type(line.typeId()).priceVersion())throw DomainException.conflict("Giá sản phẩm đã thay đổi. Hãy cập nhật báo giá và đồng ý lại trước khi giữ chỗ.");
            var q=quote(c);if(!c.acceptTerms())throw DomainException.bad("Cần chấp nhận điều khoản thuê.");
            var user=actor.current();UUID customer=user.role().equals("CUSTOMER")?user.customerId():c.customerId();if(customer==null)throw DomainException.bad("Chọn khách hàng.");identity.customer(customer);
            var assets=reservation.allocate(c.lines().stream().map(l->new ReservationApplication.Need(l.typeId(),l.quantity())).toList(),c.startsAt(),c.endsAt());
            UUID id=UUID.randomUUID();Instant expires=clock.instant().plus(holdMinutes,ChronoUnit.MINUTES);
            String terms="DEV-1: Tính ngày theo mỗi 24 giờ bắt đầu; giá và cọc được giữ theo báo giá. Giữ chỗ "+holdMinutes+" phút. Khoảng chuẩn bị/vệ sinh "+buffer+" phút mỗi đầu. Cọc tách doanh thu; hoàn cọc sau trả, kiểm tra và phê duyệt. Chính sách dev cần được chủ cơ sở xác nhận trước vận hành.";
            var o=new RentalOrder(id,r.nextCode(),customer,"","HELD",c.startsAt(),c.endsAt(),expires,(BigDecimal)q.get("rentalTotal"),(BigDecimal)q.get("depositRequired"),Checks.text(c.address(),400,"Địa chỉ nhận"),terms,"DEV-1",0);
            r.create(o,user.username());
            for(var e:assets){var t=equipment.type(e.typeId());var rate=pricing.quote(t.policyVersion(),c.startsAt(),c.endsAt(),t.dailyRate(),t.deposit(),1);var item=UUID.randomUUID();r.item(item,id,e.id(),t.name(),t.dailyRate(),rate.rental(),rate.deposit(),rate.version());reservation.hold(id,item,e.id(),c.startsAt(),c.endsAt(),expires);}
            r.captureInitialAgreement(id);audit.record("ORDER_HELD",id,o.code());audit.notifyOrder(id,"ORDER_HELD",o.code());return id;
        });
    }
    public List<Map<String,Object>> list(){var u=actor.current();if(!u.role().equals("CUSTOMER"))actor.require("OWNER","MANAGER","INVENTORY","DELIVERY","ACCOUNTANT","TECHNICIAN");return r.list(u.role().equals("CUSTOMER")?u.customerId():null);}
    public void assertAccess(UUID id){var o=required(id,false);var u=actor.current();if(u.role().equals("CUSTOMER")){if(!o.customerId().equals(u.customerId()))throw DomainException.missing();}else actor.require("OWNER","MANAGER","INVENTORY","DELIVERY","ACCOUNTANT","TECHNICIAN");}
    public Map<String,Object> detail(UUID id){assertAccess(id);return Map.of("order",required(id,false),"items",r.items(id),"balance",finance.balances(id));}
    private RentalOrder required(UUID id,boolean lock){var o=r.get(id,lock);if(o==null)throw DomainException.missing();return o;}
    @Transactional public void confirm(UUID id,String key){actor.require("OWNER","MANAGER","INVENTORY");commands.once(key,"CONFIRM:"+id,()->{var o=required(id,true);if(!o.status().equals("HELD")||!clock.instant().isBefore(o.expiresAt()))throw DomainException.conflict("Đơn không còn giữ chỗ hợp lệ.");if(finance.balances(id).depositHeld().compareTo(o.depositRequired())<0)throw DomainException.conflict("Chưa nhận đủ tiền cọc theo thỏa thuận.");verification.requireEligible(o.customerId());reservation.confirm(id);r.status(id,"CONFIRMED");audit.record("ORDER_CONFIRMED",id,o.code());audit.notifyOrder(id,"ORDER_CONFIRMED",o.code());return id;});}
    @Transactional public void prepare(UUID id,String key){actor.require("OWNER","MANAGER","INVENTORY");commands.once(key,"PREPARE:"+id,()->{var o=required(id,true);if(!o.status().equals("CONFIRMED"))throw DomainException.conflict("Chỉ chuẩn bị đơn đã xác nhận.");if(clock.instant().isBefore(o.startsAt().minus(buffer,ChronoUnit.MINUTES))||!clock.instant().isBefore(o.endsAt()))throw DomainException.conflict("Chưa đến khoảng chuẩn bị hoặc thời gian thuê đã kết thúc.");for(var i:sortedItems(id))equipment.transition((UUID)i.get("equipment_id"),Set.of("READY"),"PREPARING");r.status(id,"PREPARING");audit.record("ORDER_PREPARING",id,o.code());return id;});}
    @Transactional public void cancel(UUID id,String key){actor.require("OWNER","MANAGER","INVENTORY","CUSTOMER");commands.once(key,"CANCEL:"+id,()->{assertAccess(id);var o=required(id,true);if(!Set.of("HELD","CONFIRMED","PREPARING").contains(o.status()))throw DomainException.conflict("Không thể hủy sau khi đã bàn giao.");if(o.status().equals("PREPARING"))for(var i:sortedItems(id))equipment.transition((UUID)i.get("equipment_id"),Set.of("PREPARING"),"READY");reservation.release(id);r.status(id,"CANCELLED");r.closePendingChanges(id,"Đơn đã kết thúc bước vận hành; đề nghị chưa áp dụng được đóng.");audit.record("ORDER_CANCELLED",id,"Giữ nguyên nghĩa vụ tài chính để đối soát");audit.notifyOrder(id,"ORDER_CANCELLED",o.code());return id;});}
    @Transactional public void settle(UUID id,String key){actor.require("OWNER","ACCOUNTANT");commands.once(key,"SETTLE:"+id,()->{var o=required(id,true);if(!o.status().equals("AWAIT_SETTLEMENT"))throw DomainException.conflict("Đơn phải hoàn trả đủ thiết bị.");var b=finance.balances(id);if(b.rentalPaid().compareTo(o.rentalTotal())<0||b.chargesPaid().compareTo(b.chargesDue())<0||b.depositHeld().signum()!=0)throw DomainException.conflict("Cần nhận đủ tiền thuê và xử lý hết cọc trước tất toán.");r.status(id,"COMPLETED");r.closePendingChanges(id,"Đơn đã kết thúc bước vận hành; đề nghị chưa áp dụng được đóng.");audit.record("ORDER_COMPLETED",id,o.code());return id;});}
    private List<Map<String,Object>> sortedItems(UUID id){return r.items(id).stream().sorted(Comparator.comparing(i->i.get("equipment_id").toString())).toList();}
    private List<Map<String,Object>> selected(UUID id,List<UUID> ids){if(ids==null||ids.isEmpty()||new HashSet<>(ids).size()!=ids.size())throw DomainException.bad("Chọn ít nhất một dòng thiết bị, không trùng.");var result=sortedItems(id).stream().filter(i->ids.contains(i.get("id"))).toList();if(result.size()!=ids.size())throw DomainException.bad("Dòng thiết bị không thuộc đơn.");return result;}
    @Transactional public void handover(UUID id,List<UUID> ids){actor.require("OWNER","MANAGER","DELIVERY","INVENTORY");var o=required(id,true);if(!Set.of("CONFIRMED","PREPARING","RENTED","PARTIAL_RETURN").contains(o.status()))throw DomainException.conflict("Đơn chưa sẵn sàng bàn giao.");if(clock.instant().isBefore(o.startsAt().minus(buffer,ChronoUnit.MINUTES))||!clock.instant().isBefore(o.endsAt()))throw DomainException.conflict("Chưa đến khoảng bàn giao hoặc thời gian thuê đã kết thúc.");verification.requireEligible(o.customerId());for(var i:selected(id,ids)){if(i.get("handed_at")!=null)throw DomainException.conflict("Thiết bị đã bàn giao.");equipment.transition((UUID)i.get("equipment_id"),Set.of("READY","PREPARING"),"RENTED");r.handover((UUID)i.get("id"),clock.instant());}r.status(id,r.items(id).stream().anyMatch(i->i.get("returned_at")!=null)?"PARTIAL_RETURN":"RENTED");}
    @Transactional public void returned(UUID id,List<UUID> ids,String condition){actor.require("OWNER","MANAGER","DELIVERY","INVENTORY");var o=required(id,true);if(!Set.of("RENTED","PARTIAL_RETURN").contains(o.status()))throw DomainException.conflict("Đơn chưa có thiết bị đang thuê.");for(var i:selected(id,ids)){if(i.get("handed_at")==null||i.get("returned_at")!=null||i.get("resolved_at")!=null)throw DomainException.conflict("Thiết bị chưa giao hoặc đã trả.");equipment.transition((UUID)i.get("equipment_id"),Set.of("RENTED"),"AWAIT_INSPECTION");r.returned((UUID)i.get("id"),clock.instant(),condition);reservation.releaseItem((UUID)i.get("id"));}r.status(id,r.items(id).stream().allMatch(i->i.get("returned_at")!=null||i.get("resolved_at")!=null)?"AWAIT_SETTLEMENT":"PARTIAL_RETURN");if(r.items(id).stream().allMatch(i->i.get("returned_at")!=null||i.get("resolved_at")!=null))r.closePendingChanges(id,"Nghĩa vụ thiết bị đã xử lý đủ; không áp dụng thay đổi lịch.");}
    @Transactional public void expireHolds(){for(var id:r.expired()){r.status(id,"EXPIRED");r.closePendingChanges(id,"Đơn đã kết thúc bước vận hành; đề nghị chưa áp dụng được đóng.");reservation.release(id);audit.system("HOLD_EXPIRED",id,"Giải phóng lịch, giữ sổ tiền để đối soát");}}
}
