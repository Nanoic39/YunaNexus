package cc.nanoic.yunanexus.auth.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** 发码与注册共用的普通邮箱格式与规范化规则。 */
public final class EmailAddress {
    // auth_user, registration outbox and the user profiles currently store VARCHAR(128).
    public static final int MAX_LENGTH = 128;
    private static final Pattern ADDRESS = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
                    + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");

    private EmailAddress() {}

    public static String normalize(String email) {
        if (email == null) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (normalized.length() > MAX_LENGTH || at < 1 || at > 64) return null;
        return ADDRESS.matcher(normalized).matches() ? normalized : null;
    }
}
