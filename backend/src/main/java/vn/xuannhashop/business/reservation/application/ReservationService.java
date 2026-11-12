package vn.xuannhashop.business.reservation.application;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.reservation.application.ReservationApplication.Asset;
import vn.xuannhashop.business.shared.DomainException;
@Service
@Transactional
public class ReservationService implements ReservationApplication {
    private final ReservationRepository r;private final int buffer;
    public ReservationService(ReservationRepository r,@Value("${app.buffer-minutes}")int buffer){this.r=r;this.buffer=buffer;}
    private Instant start(Instant i){return i.minus(buffer,ChronoUnit.MINUTES);}private Instant end(Instant i){return i.plus(buffer,ChronoUnit.MINUTES);}
    public int available(UUID type,Instant from,Instant to){
        if(from==null||to==null||!to.isAfter(from))throw DomainException.bad("Chọn khoảng thời gian hợp lệ.");
        return (int)r.candidates(List.of(type),false).stream().filter(e->!r.overlaps(e.id(),start(from),end(to))).count();
    }
    public List<Asset> allocate(List<Need> needs,Instant from,Instant to){
        var assets=r.candidates(needs.stream().map(Need::typeId).toList(),true);var selected=new ArrayList<Asset>();
        for(var n:needs){var free=assets.stream().filter(e->e.typeId().equals(n.typeId())&&!r.overlaps(e.id(),start(from),end(to))).limit(n.quantity()).toList();
            if(free.size()!=n.quantity())throw DomainException.conflict("Thiết bị không còn đủ trong khoảng đã chọn. Hãy kiểm tra lại lịch.");selected.addAll(free);}
        return selected;
    }
    public void hold(UUID o,UUID i,UUID e,Instant f,Instant t,Instant x){r.hold(o,i,e,start(f),end(t),x);}
    public void confirm(UUID order){r.confirm(order);}public void release(UUID order){r.release(order);}
    public void releaseItem(UUID item){r.releaseItem(item);}public void lock(UUID e){r.lock(e);}
    public void checkChange(UUID order,List<Slot> slots){
        if(slots==null||slots.isEmpty())throw DomainException.bad("Không có thiết bị còn nghĩa vụ để thay đổi lịch.");
        if(slots.stream().map(Slot::equipmentId).distinct().count()!=slots.size())throw DomainException.bad("Không thể cấp cùng một thiết bị hai lần trong đơn.");
        var assets=r.lockChange(order,slots.stream().map(Slot::equipmentId).toList());
        for(var slot:slots){
            var asset=assets.stream().filter(a->a.id().equals(slot.equipmentId())).findFirst().orElseThrow(DomainException::missing);
            if(!asset.typeId().equals(slot.typeId())||!Set.of("READY","PREPARING","RENTED").contains(asset.state()))throw DomainException.conflict("Thiết bị không phù hợp hoặc chưa đạt điều kiện khai thác.");
            if(slot.from()==null||slot.to()==null||!slot.to().isAfter(slot.from()))throw DomainException.bad("Khoảng thuê không hợp lệ.");
            if(r.overlapsExcept(asset.id(),start(slot.from()),end(slot.to()),order))throw DomainException.conflict("Lịch mới xung đột với đơn khác hoặc thiết bị đang bị trả muộn.");
        }
    }
    public void replace(UUID order,List<Slot> slots,String status,Instant expires){
        if(!Set.of("HELD","ALLOCATED").contains(status))throw new IllegalArgumentException("Invalid reservation status");
        checkChange(order,slots);
        for(var slot:slots){r.releaseItem(slot.itemId());r.insertAllocated(order,slot.itemId(),slot.equipmentId(),start(slot.from()),end(slot.to()),expires,status);}
    }
    public void blockMaintenance(UUID window,UUID equipment,Instant from,Instant to){
        if(from==null||to==null||!to.isAfter(from))throw DomainException.bad("Khoảng bảo trì không hợp lệ.");
        var asset=r.lockAsset(equipment);if(asset==null)throw DomainException.missing();
        if(Set.of("DISPOSED","SUSPENDED").contains(asset.state()))throw DomainException.conflict("Thiết bị đã thanh lý hoặc ngừng khai thác.");
        if(r.overlaps(equipment,from,to))throw DomainException.conflict("Kế hoạch bảo trì trùng lịch thuê, khoảng chuẩn bị/vệ sinh hoặc kế hoạch khác.");
        r.blockMaintenance(window,equipment,from,to);
    }
    public void releaseMaintenance(UUID window,UUID equipment){r.lock(equipment);r.releaseMaintenance(window,equipment);}
    public void requireIdle(UUID equipment){if(r.lockAsset(equipment)==null)throw DomainException.missing();if(r.committed(equipment))throw DomainException.conflict("Thiết bị còn lịch thuê, nghĩa vụ giao/trả hoặc kế hoạch bảo trì; cần xử lý trước.");}
}
