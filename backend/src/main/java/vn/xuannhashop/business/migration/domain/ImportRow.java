package vn.xuannhashop.business.migration.domain;
import java.util.UUID;
public record ImportRow(UUID id,int rowNo,String sourceId,String name,String phone,String address,String errors) {}
