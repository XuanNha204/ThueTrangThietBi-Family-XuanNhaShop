package vn.xuannhashop.infrastructure.partners;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.support.application.NotificationPort;
@Component
@Profile({"dev","prod"})
public class DevNotificationAdapter implements NotificationPort {
    private final Path path;private final ObjectMapper json;
    public DevNotificationAdapter(@Value("${app.notifications}")String path,ObjectMapper json){this.path=Path.of(path).toAbsolutePath();this.json=json;}
    public void send(UUID id,String event,String payload)throws Exception{Files.createDirectories(path.getParent());String line=json.writeValueAsString(Map.of("id",id,"event",event,"payload",payload,"sentAt",Instant.now().toString(),"adapter","LOCAL_SPOOL"));Files.writeString(path,line+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
}
