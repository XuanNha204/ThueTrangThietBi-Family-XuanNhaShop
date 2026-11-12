package vn.xuannhashop.business.identity.domain;
import java.util.UUID;
public record Customer(UUID id, String name, String phone, String email, String address) {}
