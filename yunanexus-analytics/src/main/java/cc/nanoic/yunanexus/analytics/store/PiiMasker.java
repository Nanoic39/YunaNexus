package cc.nanoic.yunanexus.analytics.store;

/**
 * 埋点数据的 PII 脱敏。
 *
 * <p>埋点是"最全的用户画像数据源"，也是最容易被忽视的隐私风险点：
 * 一旦库被拖，原始 IP + UA 足以做设备指纹追踪。因此采集入库时就脱敏，
 * 而不是"等出事了再处理"——落库后的数据无法回收。
 *
 * @author yunanexus
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    /**
     * IP 脱敏：IPv4 保留前 3 段（/24），IPv6 保留前 3 组（/48）。
     *
     * <p>保留网段而非直接丢弃：地域与网络类型分析仍然可用，但无法定位到个人。
     *
     * @param ip 原始 IP
     * @return 脱敏后的 IP；无法识别时返回 null
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String value = ip.trim();
        if (value.contains(":")) {
            // IPv6：保留前 3 组
            String[] groups = value.split(":");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(3, groups.length); i++) {
                if (i > 0) {
                    builder.append(':');
                }
                builder.append(groups[i]);
            }
            return builder + "::";
        }
        String[] parts = value.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0";
        }
        return null;
    }

    /**
     * UA 脱敏：只保留"设备族 + 浏览器族"，丢弃版本号与完整指纹。
     *
     * @param userAgent 原始 UA
     * @return 形如 {@code mobile/Chrome} 的摘要；无法识别时返回 null
     */
    public static String maskUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent.toLowerCase(java.util.Locale.ROOT);
        String device;
        if (ua.contains("bot") || ua.contains("spider") || ua.contains("crawler")) {
            device = "bot";
        } else if (ua.contains("ipad") || ua.contains("tablet")) {
            device = "tablet";
        } else if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            device = "mobile";
        } else {
            device = "desktop";
        }
        String browser;
        if (ua.contains("edg/")) {
            browser = "Edge";
        } else if (ua.contains("chrome/")) {
            browser = "Chrome";
        } else if (ua.contains("safari/") && !ua.contains("chrome")) {
            browser = "Safari";
        } else if (ua.contains("firefox/")) {
            browser = "Firefox";
        } else {
            browser = "other";
        }
        return device + "/" + browser;
    }
}
