package vn.xuannhashop.business.reservation.application;
import java.time.Instant;
import java.util.*;
import vn.xuannhashop.business.reservation.application.ReservationApplication.Asset;
public interface ReservationRepository {
    List<Asset> candidates(List<UUID> types,boolean lock);
    boolean overlaps(UUID equipment,Instant from,Instant to);
    void hold(UUID order,UUID item,UUID equipment,Instant from,Instant to,Instant expires);
    void confirm(UUID order); void release(UUID order); void releaseItem(UUID item); void lock(UUID equipment);
    List<Asset> lockChange(UUID order,List<UUID> equipment);
    boolean overlapsExcept(UUID equipment,Instant from,Instant to,UUID order);
    void insertAllocated(UUID order,UUID item,UUID equipment,Instant from,Instant to,Instant expires,String status);
    Asset lockAsset(UUID id);
    void blockMaintenance(UUID window,UUID equipment,Instant from,Instant to);
    void releaseMaintenance(UUID window,UUID equipment);
    boolean committed(UUID equipment);
}
