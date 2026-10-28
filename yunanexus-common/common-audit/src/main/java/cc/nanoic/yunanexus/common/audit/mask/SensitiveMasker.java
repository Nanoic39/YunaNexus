package cc.nanoic.yunanexus.common.audit.mask;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 参数脱敏：审计日志本身是"可被很多人查看"的数据，绝不能把凭据写进去。<p>
 *
 * 采取"白名单式保留 + 黑名单替换"的双重策略：字段名命中敏感词直接打码，
 * 剩余值再按正则兜底（防止 token 被塞进普通字段）。
 *
 * @author yunanexus
 */
public final class SensitiveMasker {

    private static final String MASK = "***";

    /** 字段名包含这些片段时整体打码 */
    private static final String[] SENSITIVE_KEYS = {
            "password", "passwd", "pwd", "secret", "token", "credential",
            "authorization", "cookie", "session", "privatekey", "private-key"
    };

    /** 常见凭据形态：Bearer xxx、sk-xxx、JWT 三段式 */
    private static final Pattern[] SENSITIVE_PATTERNS = {
            Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._-]+"),
            Pattern.compile("(?i)sk-[A-Za-z0-9]{8,}"),
            Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")
    };

    /** 单字段最大长度，超出截断（审计参数不是日志转储） */
    private static final int MAX_VALUE_LENGTH = 512;

    private SensitiveMasker() {
    }

    /**
     * 判断字段名是否敏感。
     *
     * @param key 字段名
     * @return 命中返回 true
     */
    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        for (String token : SENSITIVE_KEYS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 脱敏一个字段值。
     *
     * @param key   字段名
     * @param value 原始值
     * @return 脱敏后的值
     */
    public static String mask(String key, String value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return MASK;
        }
        String masked = value;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll(MASK);
        }
        return masked.length() > MAX_VALUE_LENGTH ? masked.substring(0, MAX_VALUE_LENGTH) : masked;
    }

    /**
     * 把字段 Map 渲染成 {@code k=v&k2=v2} 形式并脱敏。
     *
     * @param fields 字段
     * @return 渲染结果；无字段返回 null
     */
    public static String render(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(mask(entry.getKey(), entry.getValue()));
        }
        return builder.toString();
    }

    /** 便于外部判断某些 header 是否需要丢弃 */
    public static Set<String> sensitiveKeys() {
        return Set.of(SENSITIVE_KEYS);
    }
}
