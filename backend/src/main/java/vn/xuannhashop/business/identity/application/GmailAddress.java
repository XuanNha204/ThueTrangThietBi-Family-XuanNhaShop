package vn.xuannhashop.business.identity.application;

import java.util.Locale;
import java.util.regex.Pattern;
import vn.xuannhashop.business.shared.DomainException;

public final class GmailAddress {
    private static final Pattern GMAIL = Pattern.compile("([a-z0-9]+(?:[.][a-z0-9]+)*)(?:[+][a-z0-9._+-]+)?@gmail[.]com");

    private GmailAddress() {}

    public static String required(String input) {
        var match = GMAIL.matcher(input == null ? "" : input.strip().toLowerCase(Locale.ROOT));
        if (!match.matches()) throw DomainException.bad("Vui lòng nhập địa chỉ có đuôi @gmail.com.");
        String inbox = match.group(1).replace(".", "");
        if (inbox.length() > 64) throw DomainException.bad("Địa chỉ Gmail không hợp lệ.");
        return inbox + "@gmail.com";
    }
}
