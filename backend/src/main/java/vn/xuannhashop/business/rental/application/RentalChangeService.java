package vn.xuannhashop.business.rental.application;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.rental.domain.*;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.reservation.application.ReservationApplication;
import vn.xuannhashop.business.pricing.application.PricingApplication;
import vn.xuannhashop.business.finance.application.FinanceApplication;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
@Transactional
public class RentalChangeService implements RentalChangeApplication {
    private final RentalRepository orders; private final RentalApplication rentals;
    private final RentalChangeRepository changes; private final EquipmentApplication equipment;
    private final ReservationApplication schedules; private final PricingApplication pricing;
    private final FinanceApplication finance; private final ActorContext actor;
    private final ActivityService audit; private final Commands commands; private final Clock clock;
    public RentalChangeService(RentalRepository orders,RentalApplication rentals,RentalChangeRepository changes,
            EquipmentApplication equipment,ReservationApplication schedules,PricingApplication pricing,
            FinanceApplication finance,ActorContext actor,ActivityService audit,Commands commands,Clock clock){
        this.orders=orders;this.rentals=rentals;this.changes=changes;this.equipment=equipment;
        this.schedules=schedules;this.pricing=pricing;this.finance=finance;this.actor=actor;
        this.audit=audit;this.commands=commands;this.clock=clock;
    }
    private RentalOrder order(UUID id){rentals.assertAccess(id);var o=orders.get(id,true);if(o==null)throw DomainException.missing();return o;}
    private void eligible(RentalOrder o,String kind){
        if(orders.items(o.id()).stream().anyMatch(i->Boolean.TRUE.equals(i.get("pending_incident"))))throw DomainException.conflict("Cần xử lý sự cố đang chờ duyệt trước khi đổi thỏa thuận.");
        if(!Set.of("HELD","CONFIRMED","PREPARING","RENTED","PARTIAL_RETURN").contains(o.status()))throw DomainException.conflict("Đơn không còn được thay đổi.");
        if(o.status().equals("HELD")&&!clock.instant().isBefore(o.expiresAt()))throw DomainException.conflict("Giữ chỗ đã hết hạn.");
        if(!clock.instant().isBefore(o.endsAt()))throw DomainException.conflict("Đơn đã quá hạn. Cần xử lý trả muộn, không gia hạn hồi tố.");
        if(!kind.equals("EXTEND")&&orders.items(o.id()).stream().anyMatch(i->i.get("handed_at")!=null))throw DomainException.conflict("Sau khi giao chỉ được gia hạn phần thiết bị còn thuê; không sửa lịch, địa chỉ hoặc đổi mã theo luồng trước giao.");
    }
    private List<ReservationApplication.Slot> slots(RentalChangeDraft d){return d.items().stream().filter(RentalChangeDraft.Item::outstanding).map(i->new ReservationApplication.Slot(i.itemId(),i.equipmentId(),i.typeId(),d.startsAt(),d.endsAt())).toList();}
    private RentalChangeDraft build(UUID id,Request input){
        actor.require("OWNER","MANAGER","INVENTORY","CUSTOMER");
        if(input==null||input.kind()==null||!Set.of("EXTEND","RESCHEDULE","ADDRESS","SWAP").contains(input.kind()))throw DomainException.bad("Loại thay đổi không hợp lệ.");
        var o=order(id);if(o.version()!=input.expectedVersion())throw DomainException.conflict("Đơn đã thay đổi. Hãy tải lại hồ sơ trước khi đề nghị.");
        eligible(o,input.kind());String reason=Checks.text(input.reason(),1000,"Lý do thay đổi");
        Instant from=o.startsAt(),to=o.endsAt();String address=o.address();
        if(input.kind().equals("EXTEND")){to=input.endsAt();if(to==null||!to.isAfter(o.endsAt()))throw DomainException.bad("Giờ trả mới phải sau giờ trả hiện tại.");}
        if(input.kind().equals("RESCHEDULE")){from=input.startsAt();to=input.endsAt();if(from==null||from.isBefore(clock.instant().minusSeconds(300)))throw DomainException.bad("Lịch nhận mới phải ở hiện tại hoặc tương lai.");if(from.equals(o.startsAt())&&Objects.equals(to,o.endsAt()))throw DomainException.bad("Lịch mới chưa thay đổi.");}
        if(from==null||to==null||!to.isAfter(from)||Duration.between(from,to).compareTo(Duration.ofDays(366))>0)throw DomainException.bad("Khoảng thuê mới phải hợp lệ, tối đa 366 ngày trong bản dev.");
        if(input.kind().equals("ADDRESS")){address=Checks.text(input.address(),400,"Địa chỉ mới");if(address.equals(o.address()))throw DomainException.bad("Địa chỉ mới chưa thay đổi.");}
        var original=orders.items(id);var swaps=new HashMap<UUID,UUID>();
        if(input.kind().equals("SWAP")){
            if(actor.current().role().equals("CUSTOMER"))throw DomainException.forbidden();
            if(input.swaps()==null||input.swaps().isEmpty()||input.swaps().size()>20)throw DomainException.bad("Chọn mã thiết bị cần thay.");
            for(var swap:input.swaps())if(swap==null||swap.itemId()==null||swap.equipmentId()==null||swaps.put(swap.itemId(),swap.equipmentId())!=null)throw DomainException.bad("Dòng thay thế không hợp lệ hoặc trùng.");
            if(!original.stream().map(i->(UUID)i.get("id")).toList().containsAll(swaps.keySet()))throw DomainException.bad("Dòng thay thế không thuộc đơn.");
        }else if(input.swaps()!=null&&!input.swaps().isEmpty())throw DomainException.bad("Chỉ thay mã thiết bị trong đề nghị đổi thiết bị.");
        var lines=new ArrayList<RentalChangeDraft.Item>();BigDecimal total=BigDecimal.ZERO;
        for(var item:original){
            UUID itemId=(UUID)item.get("id"),assetId=(UUID)item.get("equipment_id"),type=(UUID)item.get("type_id");String code=(String)item.get("equipment_code");
            boolean outstanding=item.get("returned_at")==null&&item.get("resolved_at")==null;
            if(swaps.containsKey(itemId)){
                UUID target=swaps.get(itemId);if(original.stream().anyMatch(i->i.get("equipment_id").equals(target)))throw DomainException.bad("Mã thay thế phải khác mọi thiết bị đang có trong đơn.");
                var asset=equipment.asset(target);if(!asset.typeId().equals(type)||!asset.state().equals("READY"))throw DomainException.bad("Chỉ đổi sang thiết bị cùng loại đang sẵn sàng.");assetId=target;code=asset.code();
            }
            BigDecimal rent=(BigDecimal)item.get("rental_amount");
            if(outstanding&&Set.of("EXTEND","RESCHEDULE").contains(input.kind()))rent=pricing.quote((String)item.get("policy_version"),from,to,(BigDecimal)item.get("daily_rate"),(BigDecimal)item.get("deposit_amount"),1).rental();
            total=total.add(rent);lines.add(new RentalChangeDraft.Item(itemId,assetId,type,code,(String)item.get("type_name"),(BigDecimal)item.get("daily_rate"),rent,(BigDecimal)item.get("deposit_amount"),(String)item.get("policy_version"),outstanding));
        }
        if(total.compareTo(finance.balances(id).rentalPaid())<0)throw DomainException.conflict("Giá mới thấp hơn tiền thuê đã nhận. Cần quy trình hoàn/điều chỉnh tiền thuê riêng.");
        var draft=new RentalChangeDraft(id,input.kind(),o.version(),from,to,address,o.rentalTotal(),total,o.depositRequired(),reason,List.copyOf(lines));
        if(!input.kind().equals("ADDRESS"))schedules.checkChange(id,slots(draft));return draft;
    }
    public RentalChangeDraft quote(UUID id,Request request){return build(id,request);}
    public UUID propose(UUID id,Request request,String key){
        return commands.once(key,"CHANGE_PROPOSE:"+id+":"+request,()->{
            if(request==null||!request.acceptTerms())throw DomainException.bad("Cần chấp nhận báo giá thay đổi.");
            var draft=build(id,request);UUID change=UUID.randomUUID();var user=actor.current();
            changes.create(change,draft,user.username(),user.role(),user.role().equals("CUSTOMER")?user.username():null);
            audit.record("RENTAL_CHANGE_PROPOSED",id,"change="+change+" / "+draft.kind());audit.notifyOrder(id,"CHANGE_PROPOSED",changes.get(change,false).draft().reason());return change;
        });
    }
    public Map<String,Object> history(UUID id){rentals.assertAccess(id);return changes.history(id);}
    private RentalChangeRepository.Proposal pending(UUID order,UUID id){
        var p=changes.get(id,true);if(p==null||!p.orderId().equals(order))throw DomainException.missing();
        if(!p.status().equals("PENDING"))throw DomainException.conflict("Đề nghị đã được xử lý.");return p;
    }
    public void accept(UUID order,UUID id,String key){actor.require("CUSTOMER");commands.once(key,"CHANGE_ACCEPT:"+order+":"+id,()->{
        var o=order(order);var p=pending(order,id);eligible(o,p.draft().kind());
        if(o.version()!=p.draft().baseVersion())throw DomainException.conflict("Đơn đã thay đổi; cần đề nghị mới.");
        if(p.customerAcceptedBy()!=null)throw DomainException.conflict("Đề nghị đã có xác nhận khách hàng.");
        changes.consent(id,actor.username(),actor.username(),"Khách chấp thuận báo giá thay đổi bằng tài khoản của mình.");audit.record("CHANGE_CUSTOMER_ACCEPTED",order,"change="+id);return id;
    });}
    public void recordConsent(UUID order,UUID id,String name,String basis,String key){actor.require("OWNER","MANAGER");commands.once(key,"CHANGE_CONSENT:"+order+":"+id+":"+name+":"+basis,()->{
        var o=order(order);var p=pending(order,id);if(o.version()!=p.draft().baseVersion())throw DomainException.conflict("Đơn đã thay đổi; cần đề nghị mới.");
        if(p.customerAcceptedBy()!=null)throw DomainException.conflict("Đề nghị đã có xác nhận khách hàng.");
        changes.consent(id,Checks.text(name,120,"Người chấp thuận"),actor.username(),Checks.text(basis,1000,"Căn cứ xác nhận của khách"));
        audit.record("CHANGE_CONSENT_RECORDED",order,"change="+id+"; xác nhận thủ công có căn cứ, không phải chữ ký số");return id;
    });}
    public void decide(UUID order,UUID id,boolean approved,String note,String key){actor.require("OWNER","MANAGER","INVENTORY");commands.once(key,"CHANGE_DECIDE:"+order+":"+id+":"+approved+":"+note,()->{
        var o=order(order);var p=pending(order,id);Checks.text(note,1000,"Ghi chú quyết định");
        if(approved){
            var d=p.draft();eligible(o,d.kind());if(o.version()!=d.baseVersion())throw DomainException.conflict("Đơn đã thay đổi sau báo giá. Không được áp dụng đề nghị cũ.");
            if(p.customerAcceptedBy()==null)throw DomainException.conflict("Cần khách hàng chấp thuận báo giá trước khi duyệt.");
            if(d.rentalTotal().compareTo(finance.balances(order).rentalPaid())<0)throw DomainException.conflict("Tiền thuê đã nhận vượt báo giá mới.");
            if(!d.kind().equals("ADDRESS")){
                schedules.replace(order,slots(d),o.status().equals("HELD")?"HELD":"ALLOCATED",o.expiresAt());
                if(d.kind().equals("SWAP"))for(var item:d.items())if(orders.items(order).stream().anyMatch(old->old.get("id").equals(item.itemId())&&!old.get("equipment_id").equals(item.equipmentId()))&&!equipment.asset(item.equipmentId()).state().equals("READY"))throw DomainException.conflict("Thiết bị thay thế không còn sẵn sàng.");
                if(o.status().equals("PREPARING")){
                    for(var old:orders.items(order))equipment.transition((UUID)old.get("equipment_id"),Set.of("PREPARING"),"READY");
                    orders.status(order,"CONFIRMED");
                }
            }
            changes.apply(id,d,actor.username());audit.record("RENTAL_CHANGE_APPLIED",order,"change="+id+"; phiên bản gốc="+d.baseVersion());audit.notifyOrder(order,"AGREEMENT_UPDATED",o.code()+" / "+d.kind());
        }else{changes.status(id,"REJECTED",actor.username(),note);audit.record("RENTAL_CHANGE_REJECTED",order,"change="+id);}
        return id;
    });}
    public void withdraw(UUID order,UUID id,String key){commands.once(key,"CHANGE_WITHDRAW:"+order+":"+id,()->{
        order(order);var p=pending(order,id);var u=actor.current();
        if(!u.role().equals("CUSTOMER")&&!p.requestedBy().equals(u.username()))actor.require("OWNER","MANAGER");
        changes.status(id,"CANCELLED",u.username(),"Đề nghị được rút hoặc khách từ chối.");audit.record("RENTAL_CHANGE_WITHDRAWN",order,"change="+id);return id;
    });}
}
