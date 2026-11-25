package vn.xuannhashop.business.identity.application;
import java.util.*;
import vn.xuannhashop.business.identity.domain.*;
public interface IdentityApplication {
    UserAccount me(); List<Customer> customers(); List<UserAccount> accounts();
    Customer customer(UUID id); UUID createCustomer(String name,String phone,String address);
    UUID register(String username,String passwordHash,String name,String phone,String address);
    record GoogleProfile(String subject, String email, String name) {}
    UserAccount googleAccount(String subject);
    UserAccount registerGoogle(GoogleProfile profile, String name, String phone, String address);
    void enable(UUID id,boolean enabled);
    record Profile(String name,String phone,String email,String address,long version) {}
    Profile profile();
    void updateProfile(Profile profile);
}
