package vn.xuannhashop.business.inventory.application;
import java.util.*;
import java.time.Instant;
import vn.xuannhashop.business.inventory.domain.*;
public interface EquipmentApplication {
    List<Map<String,Object>> catalog(Instant from,Instant to);
    EquipmentType type(UUID id); List<Map<String,Object>> assets();
    Equipment asset(UUID id);
    void transition(UUID id,Set<String> allowed,String next);
    UUID addAsset(UUID typeId,String code);
    UUID addAsset(UUID typeId,String code,UUID warehouseId);
    List<Map<String,Object>> warehouses();
    record NewType(String code,String name,String category,String description,String specifications,String icon,java.math.BigDecimal dailyRate,java.math.BigDecimal deposit) {}
    UUID createType(NewType input,String key);
}
