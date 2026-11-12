package vn.xuannhashop.business.identity.domain;
import java.util.UUID;
public record UserAccount(UUID id, String username, String displayName, String role, UUID customerId, boolean enabled) {}
