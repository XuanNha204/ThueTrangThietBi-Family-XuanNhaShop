package vn.xuannhashop.infrastructure.persistence;
import java.util.*;import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.identity.application.*;import vn.xuannhashop.business.identity.application.CustomerVerificationApplication.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository public class JdbcCustomerVerificationRepository implements CustomerVerificationRepository {
    private final Sql s;public JdbcCustomerVerificationRepository(Sql s){this.s=s;}
    public void lockPolicy(){s.rows("select pg_advisory_xact_lock(hashtextextended('eligibility-policy',0))",Map.of());}
    public Map<String,Object> policy(){return s.one("select * from rental_eligibility_policy order by version desc limit 1",Map.of());}
    public Map<String,Object> overview(){var p=policy();return Map.of("policy",p==null?Map.of("version",0,"required",false):p,"history",s.rows("select * from rental_eligibility_policy order by version desc limit 50",Map.of()),"verifications",s.rows("select v.*,c.name customer_name from customer_verification v join customer c on c.id=v.customer_id order by verified_at desc limit 300",Map.of()));}
    public Map<String,Object> customer(UUID id){return s.one("select id,version from customer where id=:id for share",p("id",id));}
    public Map<String,Object> latest(UUID id){return s.one("select * from customer_verification where customer_id=:id order by verified_at desc,id desc limit 1",p("id",id));}
    public void publish(Policy c,String actor){s.update("insert into rental_eligibility_policy(version,required,title,criteria,reason,published_by) values(:v,:required,:t,:c,:r,:a)",p("v",c.expectedVersion()+1,"required",c.required(),"t",c.title(),"c",c.criteria(),"r",c.reason(),"a",actor));}
    public void verify(UUID id,Verification c,long version,String actor){s.update("insert into customer_verification(id,customer_id,customer_version,policy_version,passed,method,reference_hint,note,valid_until,verified_by,verified_at) values(:id,:c,:v,:p,:passed,:m,:r,:n,:until,:a,clock_timestamp())",p("id",id,"c",c.customerId(),"v",version,"p",c.policyVersion(),"passed",c.passed(),"m",c.method(),"r",c.referenceHint(),"n",c.note(),"until",c.validUntil(),"a",actor));}
}
