package vn.xuannhashop.business.shared;
import java.math.BigDecimal;
public final class Checks {
    private Checks() {}
    public static String text(String value,int max,String field) {
        if(value==null || value.isBlank() || value.length()>max) throw DomainException.bad(field+" không hợp lệ (tối đa "+max+" ký tự).");
        return value.strip();
    }
    public static BigDecimal money(BigDecimal n) {
        if(n==null || n.signum()<=0 || n.scale()>2 || n.compareTo(new BigDecimal("999999999999.99"))>0) throw DomainException.bad("Số tiền phải dương, tối đa hai chữ số thập phân.");
        return n;
    }
}
