package vn.xuannhashop;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;
class ArchitectureTest {
    private final JavaClasses classes=new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("vn.xuannhashop");
    @Test void presentationCallsBusinessPorts(){
        noClasses().that().resideInAPackage("..presentation..").should().dependOnClassesThat().resideInAPackage("..infrastructure..").check(classes);
        for(var origin:classes)if(origin.getPackageName().startsWith("vn.xuannhashop.presentation."))for(var dependency:origin.getDirectDependenciesFromSelf()){
            var target=dependency.getTargetClass();
            assertThat(target.getPackageName().startsWith("vn.xuannhashop.business.")&&target.getSimpleName().endsWith("Repository")).as(dependency.getDescription()).isFalse();
        }
    }
    @Test void businessDoesNotImportAdaptersOrControllers(){
        noClasses().that().resideInAPackage("..business..").should().dependOnClassesThat().resideInAnyPackage("..infrastructure..","..presentation..").check(classes);
        String prefix="vn.xuannhashop.business.";
        for(var origin:classes)if(origin.getPackageName().startsWith(prefix))for(var dependency:origin.getDirectDependenciesFromSelf()){
            var target=dependency.getTargetClass();
            if(target.getPackageName().startsWith(prefix)&&target.getSimpleName().endsWith("Repository"))
                assertThat(origin.getPackageName().substring(prefix.length()).split("\\.")[0]).as(dependency.getDescription()).isEqualTo(target.getPackageName().substring(prefix.length()).split("\\.")[0]);
        }
    }
    @Test void pluginsCannotWriteStorageOrCallPartners(){noClasses().that().resideInAPackage("..plugins..").should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","java.sql..","java.net..","java.net.http..","java.nio.file..").check(classes);}
    @Test void businessModulesHaveNoDependencyCycles(){slices().matching("vn.xuannhashop.business.(*)..").should().beFreeOfCycles().check(classes);}
    @Test void oneBackendDeploymentUnit(){assertThat(classes.stream().filter(c->c.isAnnotatedWith(SpringBootApplication.class)).count()).isEqualTo(1);}
}
