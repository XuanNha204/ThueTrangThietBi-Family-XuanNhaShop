package vn.xuannhashop.business.identity.application;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.identity.domain.UserAccount;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class AccountAdministrationService implements AccountAdministrationApplication {
    private static final Set<String> STAFF_ROLES=Set.of("OWNER","MANAGER","ACCOUNTANT","INVENTORY","DELIVERY","TECHNICIAN","HR");
    private final IdentityRepository r;private final PasswordHashing hashing;private final ActorContext actor;private final Commands commands;private final ActivityService audit;
    public AccountAdministrationService(IdentityRepository r,PasswordHashing hashing,ActorContext actor,Commands commands,ActivityService audit){this.r=r;this.hashing=hashing;this.actor=actor;this.commands=commands;this.audit=audit;}
    @Transactional public UUID createStaff(CreateStaff c,String key){
        actor.require("OWNER");if(c==null||c.username()==null||!c.username().matches("[a-z0-9_.-]{4,60}"))throw DomainException.bad("Tên đăng nhập gồm 4–60 ký tự thường, số, chấm, gạch dưới hoặc gạch ngang.");
        PasswordPolicy.validate(c.password());
        String name=Checks.text(c.displayName(),120,"Họ tên");if(c.role()==null||!STAFF_ROLES.contains(c.role()))throw DomainException.bad("Chọn vai trò nhân viên hợp lệ.");
        // Password is used only for first execution, never in command receipts or audit text.
        return commands.once(key,"CREATE_STAFF:"+c.username()+":"+name+":"+c.role(),()->{
            r.lockAccountAdministration();if(r.find(c.username())!=null)throw DomainException.conflict("Tên đăng nhập đã tồn tại.");UUID id=UUID.randomUUID();
            r.addAccount(new UserAccount(id,c.username(),name,c.role(),null,true),hashing.encode(c.password()));audit.record("STAFF_ACCOUNT_CREATED",id,"username="+c.username()+"; role="+c.role());return id;
        });
    }
    @Transactional public void changeRole(UUID account,String expectedRole,String role,String reason,String key){
        actor.require("OWNER");if(role==null||!STAFF_ROLES.contains(role))throw DomainException.bad("Chọn vai trò nhân viên hợp lệ.");String note=Checks.text(reason,500,"Căn cứ đổi quyền");
        if(actor.current().id().equals(account))throw DomainException.bad("Không đổi vai trò của tài khoản đang sử dụng.");
        commands.once(key,"ACCOUNT_ROLE:"+account+":"+expectedRole+":"+role+":"+note,()->{
            r.lockAccountAdministration();var target=r.accounts().stream().filter(a->a.id().equals(account)).findFirst().orElseThrow(DomainException::missing);
            if(target.role().equals("CUSTOMER")||target.customerId()!=null)throw DomainException.bad("Không chuyển tài khoản khách thành nhân viên; tạo tài khoản nhân viên riêng.");
            if(!target.role().equals(expectedRole))throw DomainException.conflict("Vai trò đã thay đổi. Hãy tải lại.");
            if(target.role().equals("OWNER")&&!role.equals("OWNER")&&target.enabled()&&r.accounts().stream().filter(a->a.enabled()&&a.role().equals("OWNER")).count()<=1)throw DomainException.conflict("Phải giữ ít nhất một chủ cửa hàng hoạt động.");
            r.changeRole(account,role);audit.record("ACCOUNT_ROLE_CHANGED",account,target.role()+" -> "+role+"; "+note);return account;
        });
    }
}
