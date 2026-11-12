package vn.xuannhashop.business.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record SePayTransfer(long transactionId,String gateway,String accountNumber,
        Instant occurredAt,String transferType,BigDecimal amount,String paymentCode,
        String content,String referenceCode,String subAccount) {}
