package vn.xuannhashop.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.identity.application.IdentityRepository;
import vn.xuannhashop.business.identity.domain.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcIdentityRepository implements IdentityRepository {
    private final Sql s;public JdbcIdentityRepository(Sql s){this.s=s;}
    private UserAccount account(Map<String,Object> r){return new UserAccount((UUID)r.get("id"),(String)r.get("username"),(String)r.get("display_name"),(String)r.get("role"),(UUID)r.get("customer_id"),(Boolean)r.get("enabled"));}
    private Customer person(Map<String,Object> r){return new Customer((UUID)r.get("id"),(String)r.get("name"),(String)r.get("phone"),(String)r.get("email"),(String)r.get("address"));}
    public Credentials find(String username){var r=s.one("select * from user_account where username=:u",p("u",username));return r==null?null:new Credentials(account(r),(String)r.get("password_hash"));}
    public List<Customer> customers(){return s.rows("select * from customer order by created_at desc limit 300",Map.of()).stream().map(this::person).toList();}
    public Customer customer(UUID id){var r=s.one("select * from customer where id=:id",p("id",id));return r==null?null:person(r);}
    public void addCustomer(Customer c){s.update("insert into customer(id,name,phone,email,address) values(:id,:name,:phone,:email,:address)",p("id",c.id(),"name",c.name(),"phone",c.phone(),"email",c.email(),"address",c.address()));}
    public void addAccount(UserAccount a,String hash){s.update("insert into user_account(id,username,password_hash,display_name,role,customer_id,enabled) values(:id,:username,:hash,:display,:role,:customer,:enabled)",p("id",a.id(),"username",a.username(),"hash",hash,"display",a.displayName(),"role",a.role(),"customer",a.customerId(),"enabled",a.enabled()));}
    public List<UserAccount> accounts(){return s.rows("select * from user_account order by username",Map.of()).stream().map(this::account).toList();}
    public void enable(UUID id,boolean enabled){s.update("update user_account set enabled=:enabled where id=:id",p("id",id,"enabled",enabled));}
    public void lockAccountAdministration(){s.rows("select pg_advisory_xact_lock(hashtextextended('account-administration',0))",Map.of());}
    public void changeRole(UUID id,String role){s.update("update user_account set role=:r where id=:id",p("id",id,"r",role));}
    public vn.xuannhashop.business.identity.application.IdentityApplication.Profile profile(UUID id){var r=s.one("select * from customer where id=:id",p("id",id));return r==null?null:new vn.xuannhashop.business.identity.application.IdentityApplication.Profile((String)r.get("name"),(String)r.get("phone"),(String)r.get("email"),(String)r.get("address"),((Number)r.get("version")).longValue());}
    public boolean updateProfile(UUID id,vn.xuannhashop.business.identity.application.IdentityApplication.Profile input){
        int n=s.update("update customer set name=:n,phone=:p,email=:e,address=:a,version=version+1 where id=:id and version=:v",p("id",id,"n",input.name(),"p",input.phone(),"e",input.email(),"a",input.address(),"v",input.version()));
        if(n==1)s.update("update user_account set display_name=:n where customer_id=:id",p("id",id,"n",input.name()));return n==1;
    }
}
