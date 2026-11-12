package vn.xuannhashop.business.inventory.domain;
import java.util.UUID;
public record Equipment(UUID id,String code,UUID typeId,String state) {}
