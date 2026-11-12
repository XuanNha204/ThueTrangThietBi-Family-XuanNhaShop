package vn.xuannhashop.infrastructure.files;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.delivery.application.AttachmentRepository;
import vn.xuannhashop.business.shared.DomainException;
@Repository
public class PrivateFileRepository implements AttachmentRepository {
    private final Path root;
    @Value("${app.files.allow-pdf:true}") private boolean allowPdf;
    public PrivateFileRepository(@Value("${app.private-files}")String path)throws IOException{root=Path.of(path).toAbsolutePath().normalize();Files.createDirectories(root);}
    private Path path(String key){if(!key.matches("[0-9a-f-]{36}\\.(png|jpg|pdf)"))throw DomainException.bad("Mã tệp không hợp lệ.");var p=root.resolve(key).normalize();if(!p.getParent().equals(root))throw DomainException.forbidden();return p;}
    private String sha(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public Stored store(byte[] bytes,String filename){
        if(bytes==null||bytes.length<8||bytes.length>5*1024*1024)throw DomainException.bad("Tệp phải từ 8 byte đến 5 MB.");
        String ext,type;
        if(Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})){ext="png";type="image/png";}
        else if((bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255){ext="jpg";type="image/jpeg";}
        else if(new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")){if(!allowPdf)throw DomainException.bad("Máy chủ hiện chỉ nhận ảnh PNG/JPEG; tệp PDF chưa được bật.");ext="pdf";type="application/pdf";}
        else throw DomainException.bad("Chỉ nhận tệp PNG, JPEG hoặc PDF có chữ ký tệp hợp lệ.");
        if(!ext.equals("pdf")){
            try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
                var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw DomainException.bad("Không giải mã được ảnh.");
                var reader=readers.next();try{reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<=0||h<=0||w>8000||h>8000||(long)w*h>16000000)throw DomainException.bad("Ảnh vượt giới hạn 16 triệu điểm ảnh hoặc kích thước 8.000 px.");if(reader.read(0)==null)throw DomainException.bad("Ảnh không hợp lệ.");}finally{reader.dispose();}
            }catch(IOException e){throw DomainException.bad("Ảnh bị hỏng hoặc không đúng định dạng.");}
        }
        String key=UUID.randomUUID()+"."+ext;String name=filename==null?"bang-chung."+ext:filename.replaceAll("[^\\p{L}\\p{N}._ -]","_");if(name.length()>150)name=name.substring(name.length()-150);
        Path temporary=null;
        try{temporary=Files.createTempFile(root,"upload-",".tmp");Files.write(temporary,bytes);Files.move(temporary,path(key),StandardCopyOption.ATOMIC_MOVE);return new Stored(key,name,type,bytes.length,sha(bytes));}
        catch(IOException e){throw new DomainException(503,"Kho tệp chưa sẵn sàng. Chưa ghi nhận bằng chứng.");}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
    public Content read(String key,String checksum,String type,String name){try{byte[] b=Files.readAllBytes(path(key));if(!sha(b).equals(checksum))throw DomainException.conflict("Tệp không khớp checksum; cần kiểm tra bản sao lưu.");return new Content(b,type,name);}catch(IOException e){throw new DomainException(503,"Không đọc được tệp bằng chứng. Liên hệ người vận hành.");}}
    public void delete(String key){try{Files.deleteIfExists(path(key));}catch(IOException ignored){/* Orphan cleanup is an operations task; never remove another file. */}}
}
