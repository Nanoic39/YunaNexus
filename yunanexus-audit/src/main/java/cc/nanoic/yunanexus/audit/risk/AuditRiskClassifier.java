package cc.nanoic.yunanexus.audit.risk;

import cc.nanoic.yunanexus.audit.entity.AuditLog;

import java.util.List;
import java.util.Locale;

/**
 * 高危动作判定。<p>
 *
 * 为什么做成纯函数：判定规则是最容易"改一次错一次"的东西——必须能脱离数据库、
 * 脱离 Spring 单测，否则没人敢动它。规则表也是审计语义的一部分，任何新增都必须
 * 同时补一条用例。
 *
 * <p>分级口径：
 * <ul>
 *   <li>{@link Level#CRITICAL}：直接改变"谁能做什么"或动审计本身（提权、删号、导审计）；</li>
 *   <li>{@link Level#HIGH}：可能造成数据面影响（删除/禁用/重置凭据、跨用户访问被拒）；</li>
 *   <li>{@link Level#NONE}：常规读写，不通知（否则通知就会被忽略，等于没有通知）。</li>
 * </ul>
 *
 * @author yunanexus
 */
public final class AuditRiskClassifier {

    /** 风险级别 */
    public enum Level {
        /** 不通知 */
        NONE,
        /** 高风险 */
        HIGH,
        /** 严重 */
        CRITICAL
    }

    /**
     * 判定结果。
     *
     * @param level  级别
     * @param rule   命中的规则名（用于告警聚合与静默，不给中文文案是为了稳定）
     * @param reason 人类可读原因
     */
    public record Risk(Level level, String rule, String reason) {

        static Risk none() {
            return new Risk(Level.NONE, "", "");
        }
    }

    /** 权限相关模块/路径关键词 */
    private static final List<String> AUTHZ_HINTS =
            List.of("permission", "role", "grant", "authoriz", "acl", "menu");

    private AuditRiskClassifier() {
    }

    /**
     * 判定一条审计记录的风险级别。
     *
     * @param log 审计记录
     * @return 判定结果，不命中返回 {@link Level#NONE}
     */
    public static Risk classify(AuditLog log) {
        if (log == null) {
            return Risk.none();
        }
        String haystack = lower(log.getModule()) + ' ' + lower(log.getService()) + ' '
                + lower(log.getUri()) + ' ' + lower(log.getAction());
        String action = lower(log.getAction());
        boolean write = isWriteAction(action) || isWriteMethod(log.getMethod());
        boolean failed = log.getStatus() != null && log.getStatus() == AuditLog.STATUS_FAILED;

        // 1) 审计自身被访问（导出/查询）：能看审计的人就是能掩盖行为的人
        if (haystack.contains("/audit") || haystack.contains("audit")) {
            if (haystack.contains("export") || haystack.contains("export.csv")) {
                return new Risk(Level.CRITICAL, "audit_export",
                        "审计日志被导出：" + safe(log.getUri()));
            }
            if (haystack.contains("integrity") || haystack.contains("anchor")) {
                return new Risk(Level.CRITICAL, "audit_integrity_probe",
                        "审计完整性接口被访问：" + safe(log.getUri()));
            }
        }

        // 2) 授权面变更：提权、改角色、改菜单可见性
        boolean authz = AUTHZ_HINTS.stream().anyMatch(haystack::contains);
        if (authz && write) {
            return new Risk(Level.CRITICAL, "authz_change",
                    "授权相关变更：" + safe(log.getModule()) + ' ' + safe(log.getAction()));
        }

        // 3) 账号生命周期：删除/禁用/重置凭据
        if (write && (haystack.contains("password") || haystack.contains("reset")
                || haystack.contains("credential") || haystack.contains("token"))) {
            return new Risk(Level.HIGH, "credential_change",
                    "凭据相关变更：" + safe(log.getUri()));
        }
        if (write && (action.contains("delete") || action.contains("remove")
                || haystack.contains("disable"))) {
            return new Risk(Level.HIGH, "destructive_write",
                    "破坏性写入：" + safe(log.getMethod()) + ' ' + safe(log.getUri()));
        }

        // 4) 拒绝访问密集出现是撞库/越权的信号（单条只到 HIGH，聚合由告警侧做）
        if (failed && (haystack.contains("forbidden") || haystack.contains("403")
                || haystack.contains("denied"))) {
            return new Risk(Level.HIGH, "access_denied",
                    "越权访问被拒：" + safe(log.getUri()));
        }
        return Risk.none();
    }

    private static boolean isWriteAction(String action) {
        return action.contains("create") || action.contains("update") || action.contains("delete")
                || action.contains("remove") || action.contains("grant") || action.contains("revoke")
                || action.contains("reset") || action.contains("ban") || action.contains("enable")
                || action.contains("disable") || action.contains("publish");
    }

    private static boolean isWriteMethod(String method) {
        if (method == null) {
            return false;
        }
        String value = method.trim().toUpperCase(Locale.ROOT);
        return "POST".equals(value) || "PUT".equals(value) || "PATCH".equals(value)
                || "DELETE".equals(value);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
