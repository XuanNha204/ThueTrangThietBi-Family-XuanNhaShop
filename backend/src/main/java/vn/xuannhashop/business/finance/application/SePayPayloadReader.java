package vn.xuannhashop.business.finance.application;
import vn.xuannhashop.business.finance.domain.SePayTransfer;
public interface SePayPayloadReader {
    SePayTransfer parse(byte[] rawBody);
}
