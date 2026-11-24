package vn.xuannhashop.business.inventory.application;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.inventory.domain.*;
import vn.xuannhashop.business.shared.security.ActorContext;
import vn.xuannhashop.business.reservation.application.ReservationApplication;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
@Service
public class InventoryService implements EquipmentApplication {
    private final Commands commands;private final InventoryRepository r;private final ReservationApplication reservations;private final ActorContext actor;private final ActivityService audit;
    public InventoryService(InventoryRepository r,ReservationApplication reservations,ActorContext actor,ActivityService audit,Commands commands){this.commands=commands;this.r=r;this.reservations=reservations;this.actor=actor;this.audit=audit;}
    public List<Map<String,Object>> catalog(Instant from,Instant to){return r.catalog().stream().map(t->{var m=new LinkedHashMap<String,Object>();m.put("id",t.id());m.put("code",t.code());m.put("name",t.name());m.put("category",t.category());m.put("description",t.description());m.put("specifications",t.specifications());m.put("icon",t.icon());m.put("dailyRate",t.dailyRate());m.put("deposit",t.deposit());m.put("policyVersion",t.policyVersion());m.put("priceVersion",t.priceVersion());if(t.photo()!=null)m.put("photo",t.photo());if(from!=null&&to!=null)m.put("available",reservations.available(t.id(),from,to));return (Map<String,Object>)m;}).toList();}
    @Transactional public UUID createType(NewType c,String key){actor.require("OWNER");if(c==null)throw DomainException.bad("Thiếu loại thiết bị.");Checks.text(c.code(),30,"Mã loại");if(!c.code().matches("[A-Z0-9-]{3,30}"))throw DomainException.bad("Mã loại dùng chữ hoa, số, gạch ngang.");Checks.text(c.name(),160,"Tên loại");Checks.text(c.category(),80,"Nhóm");Checks.text(c.description(),2000,"Mô tả");Checks.text(c.specifications(),2000,"Thông số");if(!Set.of("cleaner","projector","vacuum","purifier","grill","speaker","drill","fan").contains(c.icon()))throw DomainException.bad("Biểu tượng không hợp lệ.");Checks.money(c.dailyRate());if(c.deposit()==null||c.deposit().signum()<0||c.deposit().scale()>2||c.deposit().compareTo(new java.math.BigDecimal("999999999999.99"))>0)throw DomainException.bad("Tiền cọc không hợp lệ.");return commands.once(key,"CATALOG_CREATE:"+c,()->{UUID id=UUID.randomUUID();r.createType(id,c);audit.record("CATALOG_TYPE_CREATED",id,c.code());return id;});}
    public EquipmentType type(UUID id){var t=r.type(id);if(t==null)throw DomainException.missing();return t;}
    public Equipment asset(UUID id){var e=r.asset(id);if(e==null)throw DomainException.missing();return e;}
    public List<Map<String,Object>> assets(){actor.require("OWNER","MANAGER","INVENTORY","DELIVERY","TECHNICIAN");return r.assets();}
    @Transactional public void transition(UUID id,Set<String> allowed,String next){reservations.lock(id);if(r.movementPending(id))throw DomainException.conflict("Thiết bị đang chờ chuyển kho/thanh lý hoặc đang bảo hành; xử lý hồ sơ trước.");var e=r.asset(id);if(e==null)throw DomainException.missing();if(!allowed.contains(e.state()))throw DomainException.conflict("Thiết bị "+e.code()+" đang ở trạng thái "+e.state()+".");r.state(id,next);audit.record("EQUIPMENT_STATE",id,e.state()+" → "+next);}
    public List<Map<String,Object>> warehouses(){actor.require("OWNER","MANAGER","INVENTORY","TECHNICIAN","DELIVERY");return r.warehouses();}
    @Transactional public UUID addAsset(UUID typeId,String code){return addAsset(typeId,code,(UUID)r.warehouses().get(0).get("id"));}
    @Transactional public UUID addAsset(UUID typeId,String code,UUID warehouseId){actor.require("OWNER","MANAGER","INVENTORY");type(typeId);Checks.text(code,40,"Mã thiết bị");if(!code.matches("[A-Z0-9-]{3,30}"))throw DomainException.bad("Mã thiết bị dùng chữ hoa, số và dấu gạch ngang.");var warehouses=r.warehouses();if(warehouseId==null){if(warehouses.size()!=1)throw DomainException.bad("Chọn kho tiếp nhận thiết bị.");warehouseId=(UUID)warehouses.get(0).get("id");}UUID selected=warehouseId;if(warehouses.stream().noneMatch(w->w.get("id").equals(selected)))throw DomainException.missing();var id=r.addAsset(typeId,code,warehouseId);audit.record("EQUIPMENT_RECEIVED",id,"Chờ nghiệm thu: "+code+"; kho "+warehouseId);return id;}
}
