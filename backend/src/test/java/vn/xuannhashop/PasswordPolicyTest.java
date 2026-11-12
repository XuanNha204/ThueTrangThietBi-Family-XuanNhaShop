package vn.xuannhashop;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import vn.xuannhashop.business.identity.application.PasswordPolicy;
import vn.xuannhashop.business.shared.DomainException;

class PasswordPolicyTest {
    @Test void acceptsEightCharactersWithOneOfEachGroupAndLongerPasswords() {
        for (String value : new String[]{"Abcdef1!", "XuanNha!2026", "A1!" + "a".repeat(69)})
            assertThatCode(() -> PasswordPolicy.validate(value)).doesNotThrowAnyException();
    }
    @Test void rejectsMissingGroupsShortOrOversizedPasswords() {
        for (String value : new String[]{"Abcde1!", "abcdef1!", "ABCDEF1!", "Abcdefg!", "Abcdef12", "Abcdef1 ", "Abcdef1á", "A1!" + "a".repeat(70), "A1!" + "á".repeat(35)})
            assertThatThrownBy(() -> PasswordPolicy.validate(value)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate(null)).isInstanceOf(DomainException.class);
    }
}
