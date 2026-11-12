package vn.xuannhashop.business.migration.application;
import vn.xuannhashop.business.shared.security.ActorContext;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.migration.pipeline.*;
import vn.xuannhashop.business.migration.domain.ImportRow;
import vn.xuannhashop.business.identity.application.*;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
@Service
public class MigrationService implements MigrationApplication {
    private final MigrationRepository r;private final SourceReader reader;private final RecordNormalizer normalizer;private final RecordValidator validator;private final DomainImportMapper mapper;private final StagingWriter staging;private final ImportErrorWriter errors;private final ActorContext actor;private final IdentityApplication identity;private final Commands commands;private final ActivityService audit;
    public MigrationService(MigrationRepository r,SourceReader reader,RecordNormalizer normalizer,RecordValidator validator,DomainImportMapper mapper,StagingWriter staging,ImportErrorWriter errors,ActorContext actor,IdentityApplication identity,Commands commands,ActivityService audit){this.r=r;this.reader=reader;this.normalizer=normalizer;this.validator=validator;this.mapper=mapper;this.staging=staging;this.errors=errors;this.actor=actor;this.identity=identity;this.commands=commands;this.audit=audit;}
    public List<Map<String,Object>> list(){actor.require("OWNER","INVENTORY");return r.list(actor.current().role().equals("OWNER")?null:actor.username());}
    private Map<String,Object> allowed(UUID id,boolean lock){actor.require("OWNER","INVENTORY");var b=r.batch(id,lock);if(b==null||(!actor.current().role().equals("OWNER")&&!b.get("prepared_by").equals(actor.username())))throw DomainException.missing();return b;}
    public Map<String,Object> detail(UUID id){return Map.of("batch",allowed(id,false),"rows",r.rows(id));}
    @Transactional public UUID stage(byte[] bytes,String filename,String source,String key){actor.require("OWNER","INVENTORY");Checks.text(source,100,"Mã batch");if(filename==null||!filename.toLowerCase(Locale.ROOT).endsWith(".csv"))throw DomainException.bad("Chọn tệp CSV UTF-8.");if(filename.length()>180)throw DomainException.bad("Tên tệp quá dài.");return commands.once(key,"IMPORT:"+source+":"+Base64.getEncoder().encodeToString(bytes),()->{var rows=new ArrayList<ImportRow>();var sources=new HashSet<String>();var phones=new HashSet<String>();int no=2;for(var raw:reader.read(bytes)){var normalized=normalizer.normalize(raw);rows.add(mapper.map(normalized,no++,validator.validate(normalized,sources,phones)));}var id=UUID.randomUUID();staging.write(id,source,filename,actor.username(),rows);errors.complete(id,rows);audit.record("IMPORT_STAGED",id,"Khách hàng; total="+rows.size());return id;});}
    @Transactional public void commit(UUID id,String key){actor.require("OWNER");commands.once(key,"IMPORT_COMMIT:"+id,()->{var b=allowed(id,true);if(!b.get("status").equals("STAGED"))throw DomainException.conflict("Batch chưa hợp lệ hoặc đã được nhập.");if(b.get("prepared_by").equals(actor.username()))throw DomainException.forbidden();var rows=r.rows(id);r.lockSources(rows.stream().map(row->(String)row.get("source_id")).sorted().toList());for(var row:rows){String source=(String)row.get("source_id"),phone=(String)row.get("phone");if(r.sourceExists(source)||r.phoneExists(phone))throw DomainException.conflict("Dữ liệu đã thay đổi sau staging. Đối chiếu lại mã nguồn/điện thoại.");var customer=identity.createCustomer((String)row.get("name"),phone,(String)row.get("address"));r.link((UUID)row.get("id"),source,customer);}r.committed(id,actor.username());audit.record("IMPORT_COMMITTED",id,"customers="+rows.size());return id;});}
}
