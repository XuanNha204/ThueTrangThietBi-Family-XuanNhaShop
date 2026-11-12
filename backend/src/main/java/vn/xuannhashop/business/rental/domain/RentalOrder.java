package vn.xuannhashop.business.rental.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record RentalOrder(UUID id,String code,UUID customerId,String customerName,String status,Instant startsAt,Instant endsAt,Instant expiresAt,BigDecimal rentalTotal,BigDecimal depositRequired,String address,String terms,String agreementVersion,long version) {}
