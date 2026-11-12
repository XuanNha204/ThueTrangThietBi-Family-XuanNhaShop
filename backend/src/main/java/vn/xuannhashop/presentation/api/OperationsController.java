package vn.xuannhashop.presentation.api;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.xuannhashop.business.identity.application.IdentityApplication;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import vn.xuannhashop.business.delivery.application.DeliveryApplication;
import vn.xuannhashop.business.maintenance.application.MaintenanceApplication;
import vn.xuannhashop.business.finance.application.FinanceApplication;
import vn.xuannhashop.business.payroll.application.PayrollApplication;
import vn.xuannhashop.business.support.application.SupportApplication;
import vn.xuannhashop.business.reporting.application.ReportingApplication;
import vn.xuannhashop.business.shared.audit.ActivityService;
@RestController
@RequestMapping("/api")
public class OperationsController {
    private final IdentityApplication identity;private final EquipmentApplication equipment;private final DeliveryApplication delivery;private final MaintenanceApplication maintenance;private final FinanceApplication finance;private final PayrollApplication payroll;private final SupportApplication support;private final ReportingApplication reports;private final ActivityService audit;
    public OperationsController(IdentityApplication i,EquipmentApplication e,DeliveryApplication d,MaintenanceApplication m,FinanceApplication f,PayrollApplication p,SupportApplication s,ReportingApplication r,ActivityService a){identity=i;equipment=e;delivery=d;maintenance=m;finance=f;payroll=p;support=s;reports=r;audit=a;}
    record CustomerInput(String name,String phone,String address) {}record AssetInput(UUID typeId,String code,UUID warehouseId) {}record Enabled(boolean enabled) {}
    record HandoverInput(List<UUID> items,String acceptedBy,String condition,String note) {}
    record Fault(String fault) {}
    record MoneyInput(UUID orderId,String kind,BigDecimal amount,String note) {}record RefundInput(UUID orderId,BigDecimal amount,String reason) {}record Decision(boolean approved) {}
    record WorkInput(UUID staffId,LocalDate date,BigDecimal hours) {}record DraftInput(UUID staffId,String period) {}
    record TicketInput(UUID orderId,String subject,String message) {}record Resolution(String resolution) {}
    @GetMapping("/customers") Object customers(){return identity.customers();}
    @PostMapping("/customers") Object customer(@RequestBody CustomerInput c){return Map.of("id",identity.createCustomer(c.name(),c.phone(),c.address()));}
    @GetMapping("/accounts") Object accounts(){return identity.accounts();}
    @PatchMapping("/accounts/{id}") void account(@PathVariable UUID id,@RequestBody Enabled enabled){identity.enable(id,enabled.enabled());}
    @PostMapping("/equipment-types") Object createType(@RequestBody EquipmentApplication.NewType c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",equipment.createType(c,key));}
    @GetMapping("/equipment") Object equipment(){return equipment.assets();}
    @GetMapping("/warehouses") Object warehouses(){return equipment.warehouses();}
    @PostMapping("/equipment") Object asset(@RequestBody AssetInput c){return Map.of("id",equipment.addAsset(c.typeId(),c.code(),c.warehouseId()));}
    @PostMapping("/rentals/{id}/handover") Object handover(@PathVariable UUID id,@RequestBody HandoverInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",delivery.handover(id,c.items(),c.acceptedBy(),c.note(),key));}
    @PostMapping("/rentals/{id}/return") Object returned(@PathVariable UUID id,@RequestBody HandoverInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",delivery.returned(id,c.items(),c.acceptedBy(),c.condition(),c.note(),key));}
    @GetMapping("/rentals/{id}/handovers") Object history(@PathVariable UUID id){return delivery.history(id);}
    @GetMapping("/rentals/{id}/evidence") Object evidence(@PathVariable UUID id){return delivery.evidence(id);}
    @PostMapping("/rentals/{id}/evidence") Object upload(@PathVariable UUID id,@RequestParam MultipartFile file)throws Exception{return Map.of("id",delivery.upload(id,file.getBytes(),file.getOriginalFilename()));}
    @GetMapping("/evidence/{id}") ResponseEntity<byte[]> download(@PathVariable UUID id){var c=delivery.download(id);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(c.name(),StandardCharsets.UTF_8).build().toString()).header(HttpHeaders.CACHE_CONTROL,"no-store").contentType(MediaType.parseMediaType(c.contentType())).body(c.bytes());}
    @GetMapping("/maintenance") Object maintenance(){return Map.of("inspections",maintenance.inspections(),"tickets",maintenance.tickets());}
    @PostMapping("/equipment/{id}/inspection") Object inspect(@PathVariable UUID id,@RequestBody vn.xuannhashop.business.maintenance.application.InspectionChecklistApplication.Input c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",maintenance.inspect(id,c,key));}
    @PostMapping("/equipment/{id}/maintenance") Object fault(@PathVariable UUID id,@RequestBody Fault c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",maintenance.open(id,c.fault(),key));}
    @GetMapping("/finance/ledger") Object ledger(){return finance.ledger();}
    @GetMapping("/finance/approvals") Object approvals(){return finance.approvals();}
    @PostMapping("/finance/receive") Object receive(@RequestBody MoneyInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",finance.receive(c.orderId(),c.kind(),c.amount(),c.note(),key));}
    @PostMapping("/finance/refunds") Object refund(@RequestBody RefundInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",finance.requestRefund(c.orderId(),c.amount(),c.reason(),key));}
    @PostMapping("/finance/approvals/{id}") void decision(@PathVariable UUID id,@RequestBody Decision c,@RequestHeader("Idempotency-Key")String key){finance.decide(id,c.approved(),key);}
    @GetMapping("/payroll") Object payroll(){return payroll.overview();}
    @PostMapping("/payroll/work") Object work(@RequestBody WorkInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",payroll.work(c.staffId(),c.date(),c.hours(),key));}
    @PostMapping("/payroll/work/{id}/approve") void approveWork(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){payroll.approveWork(id,key);}
    @PostMapping("/payroll") Object draft(@RequestBody DraftInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",payroll.draft(c.staffId(),c.period(),key));}
    @PostMapping("/payroll/{id}/approve") void approve(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){payroll.approve(id,key);}
    @PostMapping("/payroll/{id}/pay") void pay(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){payroll.pay(id,key);}
    @GetMapping("/support") Object support(){return support.tickets();}
    @PostMapping("/support") Object ticket(@RequestBody TicketInput c,@RequestHeader("Idempotency-Key")String key){return Map.of("id",support.create(c.orderId(),c.subject(),c.message(),key));}
    @PostMapping("/support/{id}/resolve") void resolve(@PathVariable UUID id,@RequestBody Resolution c,@RequestHeader("Idempotency-Key")String key){support.resolve(id,c.resolution(),key);}
    @GetMapping("/notifications") Object notifications(){return support.notifications();}
    @GetMapping("/dashboard") Object dashboard(){return reports.dashboard();}
    @GetMapping("/audit") Object audit(){return audit.recent();}
    @GetMapping(value="/reports/finance.csv",produces="text/csv;charset=UTF-8") ResponseEntity<String> csv(@RequestParam LocalDate from,@RequestParam LocalDate until){var rows=reports.ledger(from,until);var b=new StringBuilder("\ufeffThời điểm,Mã đơn,Loại,Số tiền,Người ghi,Căn cứ\r\n");for(var row:rows){var fields=List.of("created_at","order_code","kind","amount","recorded_by","note");b.append(String.join(",",fields.stream().map(k->cell(row.get(k))).toList())).append("\r\n");}return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=xuannhashop-finance.csv").body(b.toString());}
    private String cell(Object value){String s=value==null?"":value.toString();if(s.matches("^[=+@\\-\\t\\r].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
