package vn.xuannhashop.business.shared.security;
import vn.xuannhashop.business.shared.security.ActorContext;
import vn.xuannhashop.business.shared.DomainException;
public interface ActorContext {
    ActorSnapshot current();
    default void require(String... roles){var u=current();for(var role:roles)if(u.role().equals(role))return;throw DomainException.forbidden();}
    default String username(){return current().username();}
}
