package vn.xuannhashop.business.migration.pipeline;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.migration.application.MigrationRepository;
import vn.xuannhashop.business.migration.domain.ImportRow;
@Component
public class ImportErrorWriter {private final MigrationRepository r;public ImportErrorWriter(MigrationRepository r){this.r=r;}public void complete(UUID id,List<ImportRow> rows){r.summarize(id,rows.size(),(int)rows.stream().filter(x->x.errors().isEmpty()).count());}}
