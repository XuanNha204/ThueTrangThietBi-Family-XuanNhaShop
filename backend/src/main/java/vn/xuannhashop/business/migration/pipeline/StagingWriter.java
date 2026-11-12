package vn.xuannhashop.business.migration.pipeline;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.migration.application.MigrationRepository;
import vn.xuannhashop.business.migration.domain.ImportRow;
@Component
public class StagingWriter {private final MigrationRepository r;public StagingWriter(MigrationRepository r){this.r=r;}public void write(UUID id,String source,String filename,String actor,List<ImportRow> rows){r.stage(id,source,filename,actor,rows);}}
