package vn.xuannhashop.business.inventory.domain;
import java.math.BigDecimal;
import java.util.UUID;
public record EquipmentType(UUID id,String code,String name,String category,String description,String specifications,String icon,BigDecimal dailyRate,BigDecimal deposit,String policyVersion,long priceVersion) {}
