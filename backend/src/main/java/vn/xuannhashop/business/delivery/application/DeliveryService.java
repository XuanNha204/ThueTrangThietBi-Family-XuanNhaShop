package vn.xuannhashop.business.delivery.application;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import vn.xuannhashop.business.rental.application.RentalApplication;
import vn.xuannhashop.business.shared.security.ActorContext;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
@Service
public class DeliveryService implements DeliveryApplication {
    private final DeliveryPlanningApplication planning;private final DeliveryRepository r;private final RentalApplication rentals;private final AttachmentRepository files;private final ActorContext actor;private final Commands commands;private final ActivityService audit;
    public DeliveryService(DeliveryPlanningApplication planning,DeliveryRepository r,RentalApplication rentals,AttachmentRepository files,ActorContext actor,Commands commands,ActivityService audit){this.planning=planning;this.r=r;this.rentals=rentals;this.files=files;this.actor=actor;this.commands=commands;this.audit=audit;}
    @Transactional public UUID handover(UUID order,List<UUID> items,String accepted,String note,String key){actor.require("OWNER","MANAGER","DELIVERY","INVENTORY");Checks.text(accepted,120,"Người xác nhận");Checks.text(note,1000,"Ghi chú bàn giao");return commands.once(key,"HANDOVER:"+order+":"+items+":"+accepted+":"+note,()->{planning.requireAcknowledged(order,items);rentals.handover(order,items);var id=r.record(order,items,"OUT",accepted,"GOOD",note,actor.username());audit.record("HANDOVER_RECORDED",order,"handover="+id);audit.notifyOrder(order,"HANDED_OVER",accepted);return id;});}
    @Transactional public UUID returned(UUID order,List<UUID> items,String accepted,String condition,String note,String key){actor.require("OWNER","MANAGER","DELIVERY","INVENTORY");Checks.text(accepted,120,"Người xác nhận");Checks.text(note,1000,"Ghi chú thu hồi");if(condition==null||!Set.of("GOOD","DAMAGED").contains(condition))throw DomainException.bad("Chọn tình trạng tốt hoặc có sự cố.");return commands.once(key,"RETURN:"+order+":"+items+":"+accepted+":"+condition+":"+note,()->{rentals.returned(order,items,condition);var id=r.record(order,items,"IN",accepted,condition,note,actor.username());audit.record("RETURN_RECORDED",order,"handover="+id+"; "+condition);audit.notifyOrder(order,"RETURNED",condition);return id;});}
    public List<Map<String,Object>> history(UUID order){rentals.assertAccess(order);return r.list(order);}
    public List<Map<String,Object>> evidence(UUID order){rentals.assertAccess(order);return r.evidenceFor(order);}
    @Transactional public UUID upload(UUID order,byte[] bytes,String filename){actor.require("OWNER","MANAGER","DELIVERY","INVENTORY","TECHNICIAN");rentals.assertAccess(order);var stored=files.store(bytes,filename);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCompletion(int status){if(status!=STATUS_COMMITTED)files.delete(stored.key());}});var id=UUID.randomUUID();r.evidence(id,order,stored,actor.username());audit.record("EVIDENCE_ATTACHED",order,"evidence="+id+"; sha256="+stored.checksum());return id;}
    public AttachmentRepository.Content download(UUID id){var row=r.evidence(id);if(row==null)throw DomainException.missing();rentals.assertAccess((UUID)row.get("order_id"));return files.read((String)row.get("file_key"),(String)row.get("checksum"),(String)row.get("content_type"),(String)row.get("original_name"));}
}
