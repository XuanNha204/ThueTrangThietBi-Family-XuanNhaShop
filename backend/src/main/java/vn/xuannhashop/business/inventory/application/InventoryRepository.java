package vn.xuannhashop.business.inventory.application;
import java.util.*;
import vn.xuannhashop.business.inventory.domain.*;
public interface InventoryRepository {
    List<EquipmentType> catalog(); EquipmentType type(UUID id);
    List<Map<String,Object>> assets(); Equipment asset(UUID id); void state(UUID id,String state);
    UUID addAsset(UUID typeId,String code,UUID warehouseId); List<Map<String,Object>> warehouses();
    void createType(UUID id,EquipmentApplication.NewType input);
    boolean movementPending(UUID id);
}
