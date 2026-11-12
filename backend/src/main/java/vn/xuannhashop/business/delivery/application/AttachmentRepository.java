package vn.xuannhashop.business.delivery.application;
public interface AttachmentRepository {
    record Stored(String key,String originalName,String contentType,long size,String checksum) {}
    record Content(byte[] bytes,String contentType,String name) {}
    Stored store(byte[] bytes,String filename);
    Content read(String key,String checksum,String contentType,String name);
    void delete(String key);
}
