package vn.xuannhashop.business.reservation.application;
import java.time.Instant;
import java.util.*;
public interface ReservationApplication {
    record Need(UUID typeId,int quantity) {}
    record Asset(UUID id,String code,UUID typeId,String state) {}
    record Slot(UUID itemId,UUID equipmentId,UUID typeId,Instant from,Instant to) {}
    int available(UUID typeId,Instant from,Instant to);
    List<Asset> allocate(List<Need> needs,Instant from,Instant to);
    void hold(UUID order,UUID item,UUID equipment,Instant from,Instant to,Instant expires);
    void confirm(UUID order); void release(UUID order); void releaseItem(UUID item); void lock(UUID equipment);
    void checkChange(UUID order,List<Slot> slots);
    void replace(UUID order,List<Slot> slots,String status,Instant expires);
    void blockMaintenance(UUID window,UUID equipment,Instant from,Instant to);
    void releaseMaintenance(UUID window,UUID equipment);
    void requireIdle(UUID equipment);
}
