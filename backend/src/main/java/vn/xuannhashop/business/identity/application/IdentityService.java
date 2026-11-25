package vn.xuannhashop.business.identity.application;
import vn.xuannhashop.business.shared.security.ActorContext;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.identity.domain.*;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
@Service
public class IdentityService implements IdentityApplication {
    private final IdentityRepository repository;private final ActorContext actor;private final ActivityService audit;
    public IdentityService(IdentityRepository r,ActorContext a,ActivityService audit){repository=r;actor=a;this.audit=audit;}
    public UserAccount me(){var a=actor.current();return new UserAccount(a.id(),a.username(),a.displayName(),a.role(),a.customerId(),a.enabled());}
    public List<Customer> customers(){actor.require("OWNER","MANAGER","ACCOUNTANT","INVENTORY","DELIVERY");return repository.customers();}
    public Customer customer(UUID id){var c=repository.customer(id);if(c==null)throw DomainException.missing();return c;}
    @Transactional public UUID createCustomer(String name,String phone,String address){actor.require("OWNER","MANAGER","INVENTORY");var c=validated(name,phone,address);repository.addCustomer(c);audit.record("CUSTOMER_CREATED",c.id(),"Tạo hồ sơ khách hàng");return c.id();}
    @Transactional public UUID register(String username,String passwordHash,String name,String phone,String address){
        if(username==null||!username.matches("[a-z0-9_.-]{4,60}"))throw DomainException.bad("Tên đăng nhập gồm 4–60 ký tự thường, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        if(repository.find(username)!=null)throw DomainException.conflict("Tên đăng nhập đã tồn tại.");
        var c=validated(name,phone,address);repository.addCustomer(c);var id=UUID.randomUUID();
        repository.addAccount(new UserAccount(id,username,c.name(),"CUSTOMER",c.id(),true),passwordHash);return id;
    }
    @Override
    public UserAccount googleAccount(String subject) {
        UserAccount account = repository.findByGoogleSubject(Checks.text(subject, 255, "Danh tính Google"));
        if (account != null && (!account.enabled() || !"CUSTOMER".equals(account.role()) || account.customerId() == null)) {
            throw new DomainException(403, "Tài khoản khách thuê không thể đăng nhập.");
        }
        return account;
    }

    @Override
    @Transactional
    public UserAccount registerGoogle(GoogleProfile profile, String name, String phone, String address) {
        String subject = Checks.text(profile.subject(), 255, "Danh tính Google");
        String email = validatedEmail(Checks.text(profile.email(), 160, "Email Google"));
        Customer details = validated(name, phone, address);
        repository.lockGoogleSubject(subject);
        UserAccount existing = googleAccount(subject);
        if (existing != null) return existing;

        Customer customer = new Customer(details.id(), details.name(), details.phone(), email, details.address());
        UUID accountId = UUID.randomUUID();
        String username = "google-" + accountId;
        var account = new UserAccount(accountId, username, customer.name(), "CUSTOMER", customer.id(), true);
        repository.addCustomer(customer);
        repository.addAccount(account, null);
        repository.bindGoogleSubject(subject, accountId);
        audit.system("GOOGLE_CUSTOMER_REGISTERED", accountId, "Tạo tài khoản khách từ đăng nhập Google.");
        return account;
    }
    private Customer validated(String name,String phone,String address){
        Checks.text(phone,20,"Điện thoại");if(!phone.matches("\\+?[0-9]{8,15}"))throw DomainException.bad("Số điện thoại gồm 8–15 chữ số.");
        return new Customer(UUID.randomUUID(),Checks.text(name,120,"Họ tên"),phone,null,Checks.text(address,400,"Địa chỉ"));
    }
    public List<UserAccount> accounts(){actor.require("OWNER");return repository.accounts();}
    @Transactional public void enable(UUID id,boolean enabled){actor.require("OWNER");if(actor.current().id().equals(id))throw DomainException.bad("Không khóa tài khoản đang sử dụng.");repository.lockAccountAdministration();var target=repository.accounts().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(DomainException::missing);if(!enabled&&target.role().equals("OWNER")&&target.enabled()&&repository.accounts().stream().filter(a->a.enabled()&&a.role().equals("OWNER")).count()<=1)throw DomainException.conflict("Phải giữ ít nhất một chủ cửa hàng hoạt động.");repository.enable(id,enabled);audit.record("ACCOUNT_STATUS",id,"enabled="+enabled);}
    public Profile profile(){actor.require("CUSTOMER");var p=repository.profile(actor.current().customerId());if(p==null)throw DomainException.missing();return p;}
    @Transactional public void updateProfile(Profile input){
        actor.require("CUSTOMER");if(input==null)throw DomainException.bad("Thông tin liên hệ không hợp lệ.");
        var c=validated(input.name(),input.phone(),input.address());String email=validatedEmail(input.email());
        UUID id=actor.current().customerId();
        if(!repository.updateProfile(id,new Profile(c.name(),c.phone(),email,c.address(),input.version())))throw DomainException.conflict("Thông tin đã được cập nhật ở phiên khác. Hãy tải lại trước khi lưu.");
        audit.record("CUSTOMER_CONTACT_UPDATED",id,"Cập nhật liên hệ của chính mình; giữ nguyên địa chỉ trong các đơn đã lập.");
    }

    private String validatedEmail(String value) {
        String email = value == null ? null : value.strip();
        if (email == null || email.isEmpty()) return null;
        if (email.length() > 160 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw DomainException.bad("Email không hợp lệ.");
        }
        return email;
    }
}
