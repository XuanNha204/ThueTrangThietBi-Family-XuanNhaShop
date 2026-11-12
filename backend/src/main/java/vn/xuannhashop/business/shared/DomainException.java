package vn.xuannhashop.business.shared;

public class DomainException extends RuntimeException {
    private final int status;
    public DomainException(int status, String message) { super(message); this.status=status; }
    public int status() { return status; }
    public static DomainException bad(String m) { return new DomainException(400,m); }
    public static DomainException forbidden() { return new DomainException(403,"Bạn không có quyền thực hiện thao tác này."); }
    public static DomainException missing() { return new DomainException(404,"Không tìm thấy dữ liệu."); }
    public static DomainException conflict(String m) { return new DomainException(409,m); }
}
