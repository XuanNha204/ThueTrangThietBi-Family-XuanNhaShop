package vn.xuannhashop.business.maintenance.plugins;
import org.springframework.stereotype.Component;
import vn.xuannhashop.business.rules.InspectionRule;
@Component("inspectionDEV1")
public class InspectionPlugin implements InspectionRule {
    public boolean safe(boolean powerSafe,boolean clean,boolean complete){return powerSafe&&clean&&complete;}
}
