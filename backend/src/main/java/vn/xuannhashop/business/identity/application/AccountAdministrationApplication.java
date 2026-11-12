package vn.xuannhashop.business.identity.application;

import java.util.UUID;

public interface AccountAdministrationApplication {
    record CreateStaff(String username,String password,String displayName,String role) {
        @Override public String toString(){return "CreateStaff[username="+username+", password=REDACTED]";}
    }
    UUID createStaff(CreateStaff input,String key);
    void changeRole(UUID account,String expectedRole,String role,String reason,String key);
}
