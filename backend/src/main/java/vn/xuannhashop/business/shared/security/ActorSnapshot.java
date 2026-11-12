package vn.xuannhashop.business.shared.security;
import java.util.UUID;
/** Immutable authentication contract, separate from B01's account aggregate. */
public record ActorSnapshot(UUID id,String username,String displayName,String role,UUID customerId,boolean enabled) {}
