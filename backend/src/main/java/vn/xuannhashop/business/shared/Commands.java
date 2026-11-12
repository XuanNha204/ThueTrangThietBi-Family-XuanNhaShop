package vn.xuannhashop.business.shared;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.xuannhashop.business.shared.security.ActorContext;
@Component
public class Commands {
    private final IdempotencyRepository repository;private final ActorContext actor;
    public Commands(IdempotencyRepository repository,ActorContext actor) { this.repository=repository;this.actor=actor; }
    public UUID once(String key,String payload,Supplier<UUID> action) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Command requires a transaction");
        Checks.text(key,120,"Mã thao tác");
        String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        repository.lock(key);var old=repository.find(key);var who=actor.username();
        if(old!=null) {
            if(!old.actor().equals(who)||!old.fingerprint().equals(fingerprint)) throw DomainException.conflict("Mã thao tác đã được dùng cho một yêu cầu khác.");
            return old.resultId();
        }
        UUID id=action.get();repository.save(key,who,fingerprint,id);return id;
    }
}
