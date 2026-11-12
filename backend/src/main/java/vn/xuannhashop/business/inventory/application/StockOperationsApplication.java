package vn.xuannhashop.business.inventory.application;
import java.util.*;
import java.time.LocalDate;
import java.math.BigDecimal;
public interface StockOperationsApplication {
    record ReceiptLine(String code,String serialNumber) {}
    record Receipt(UUID warehouseId,UUID typeId,String supplierName,String documentRef,LocalDate acquiredOn,LocalDate warrantyUntil,Integer usefulLifeMonths,BigDecimal unitCost,List<ReceiptLine> lines,String reason) {}
    record Action(String kind,UUID equipmentId,UUID toWarehouse,BigDecimal recoveryValue,String reason) {}
    record Count(UUID warehouseId,List<String> observedCodes,String reason) {}
    Map<String,Object> overview();
    UUID warehouse(String name,String address,String key);
    UUID receive(Receipt input,String key);
    void decideReceipt(UUID id,boolean accepted,String note,String key);
    UUID action(Action input,String key);
    void decideAction(UUID id,boolean accepted,String note,String key);
    UUID count(Count input,String key);
    void decideCount(UUID id,boolean accepted,String note,String key);
}
