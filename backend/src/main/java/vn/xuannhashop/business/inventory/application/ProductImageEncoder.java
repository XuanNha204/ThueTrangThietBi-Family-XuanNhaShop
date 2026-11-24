package vn.xuannhashop.business.inventory.application;

public interface ProductImageEncoder {
    byte[] toJpeg(byte[] image);
}
