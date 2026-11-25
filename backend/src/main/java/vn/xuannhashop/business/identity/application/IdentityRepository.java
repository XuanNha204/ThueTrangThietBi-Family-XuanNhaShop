package vn.xuannhashop.business.identity.application;
import java.util.*;
import vn.xuannhashop.business.identity.domain.*;
public interface IdentityRepository {
    record Credentials(UserAccount account,String passwordHash) {}
    Credentials find(String username);
    Credentials findByLoginGmail(String gmail);
    IdentityApplication.LoginIdentity loginIdentity(String username);
    IdentityApplication.LoginIdentity loginIdentity(UUID accountId);
    void lockGmail(String gmail);
    boolean assignGmail(UUID account, String gmail);
    UserAccount findByGoogleSubject(String subject);
    void lockGoogleSubject(String subject);
    void bindGoogleSubject(String subject, UUID account);
    List<Customer> customers();
    Customer customer(UUID id);
    void addCustomer(Customer c);
    void addAccount(UserAccount a,String passwordHash);
    List<UserAccount> accounts();
    void enable(UUID id,boolean enabled);
    void lockAccountAdministration();
    void changeRole(UUID id,String role);
    IdentityApplication.Profile profile(UUID customer);
    boolean updateProfile(UUID customer,IdentityApplication.Profile profile);
}
