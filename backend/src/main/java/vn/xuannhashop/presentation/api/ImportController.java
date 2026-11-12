package vn.xuannhashop.presentation.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.xuannhashop.business.migration.application.MigrationApplication;
@RestController
@RequestMapping("/api/imports")
public class ImportController {
    private final MigrationApplication migration;public ImportController(MigrationApplication m){migration=m;}
    @GetMapping Object list(){return migration.list();}
    @GetMapping("/{id}") Object detail(@PathVariable UUID id){return migration.detail(id);}
    @PostMapping Object stage(@RequestParam MultipartFile file,@RequestParam String sourceKey,@RequestHeader("Idempotency-Key")String key)throws Exception{return Map.of("id",migration.stage(file.getBytes(),file.getOriginalFilename(),sourceKey,key));}
    @PostMapping("/{id}/commit") void commit(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key){migration.commit(id,key);}
}
