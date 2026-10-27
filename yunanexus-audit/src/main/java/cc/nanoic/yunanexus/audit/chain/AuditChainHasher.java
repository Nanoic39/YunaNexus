package cc.nanoic.yunanexus.audit.chain;

import cc.nanoic.yunanexus.audit.entity.AuditLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 审计记录的防篡改摘要计算。<p>
 *
 * 设计要点（这些取舍都有原因，改动前请先看）：
 * <ul>
 *   <li><b>链式而非签名</b>：不引入密钥管理，靠 {@code prev_hash} 把记录串成单向链。
 *       单条被改写会让其后全部断裂，篡改位置可精确定位；代价是"整库重写并重算全链"
 *       这一攻击面无法靠摘要本身防住——那需要外部锚定（对象存储 WORM / 第三方时间戳），
 *       属于下一增量的内容。</li>
 *   <li><b>规范化用不可见分隔符</b>：字段值本身可能含逗号、引号、换行，用 {@code \\u001f}
 *       分隔可避免"字段拼接歧义"导致的摘要碰撞（例如 a="x,y" 与 b="x" 拼接后相同）。</li>
 *   <li><b>空值统一成固定标记</b>：{@code null} 与字面量 "null" 必须区分开，否则伪造
 *       "null" 字符串即可绕过。</li>
 *   <li><b>时间精度截到毫秒</b>：MySQL {@code DATETIME(3)} 的实际精度，避免 Java 纳秒
 *       部分造成"库里的值和算出来的值不一致"的假告警。</li>
 * </ul>
 *
 * @author yunanexus
 */
public final class AuditChainHasher {

    /** 链首的前驱摘要：64 个 0 */
    public static final String GENESIS = "0".repeat(64);

    /** 字段分隔符（单元分隔符，正文中不会出现） */
    private static final String SEP = "\u001f";

    /** 空值占位：与字面量 "null" 区分 */
    private static final String NULL_MARK = "\u0000null";

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private AuditChainHasher() {
    }

    /**
     * 计算一条记录的规范化摘要（不含链信息）。<p>
     *
     * 参与摘要的字段即"业务上不可篡改的事实"，新增字段必须同步加入，否则改它不会被发现。
     *
     * @param log 审计记录
     * @return 64 位十六进制摘要
     */
    public static String digest(AuditLog log) {
        return sha256Hex(canonical(log));
    }

    /**
     * 计算链上摘要：{@code SHA256(prevHash + SEP + canonical(log))}。
     *
     * @param prevHash 上一条记录的 {@code record_hash}；链首传 {@link #GENESIS}
     * @param log      当前记录
     * @return 64 位十六进制摘要
     */
    public static String chainDigest(String prevHash, AuditLog log) {
        String previous = (prevHash == null || prevHash.isEmpty()) ? GENESIS : prevHash;
        return sha256Hex(previous + SEP + canonical(log));
    }

    /**
     * 校验一条记录是否与给定前驱摘要匹配。
     *
     * @param log          待校验记录（须带 {@code recordHash}）
     * @param prevHash     期望的前驱摘要
     * @return 匹配返回 {@code true}
     */
    public static boolean verifies(AuditLog log, String prevHash) {
        if (log == null || log.getRecordHash() == null) {
            return false;
        }
        return chainDigest(prevHash, log).equalsIgnoreCase(log.getRecordHash());
    }

    /**
     * 把记录序列化成规范化字符串（包级可见，便于测试逐字段比对）。
     *
     * @param log 审计记录
     * @return 规范化字符串
     */
    static String canonical(AuditLog log) {
        StringBuilder builder = new StringBuilder(256);
        append(builder, log.getUserId());
        append(builder, log.getShardIndex());
        append(builder, log.getUsername());
        append(builder, log.getService());
        append(builder, log.getModule());
        append(builder, log.getAction());
        append(builder, log.getMethod());
        append(builder, log.getUri());
        append(builder, log.getParams());
        append(builder, log.getResult());
        append(builder, log.getStatus());
        append(builder, log.getErrorMsg());
        append(builder, log.getIp());
        append(builder, log.getUserAgent());
        append(builder, log.getDurationMs());
        append(builder, log.getTraceId());
        append(builder, format(log.getCreateTime()));
        return builder.toString();
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMAT.format(time);
    }

    private static void append(StringBuilder builder, Object value) {
        builder.append(SEP);
        if (value == null) {
            builder.append(NULL_MARK);
            return;
        }
        builder.append(value);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            // JDK 规范要求 SHA-256 必须存在，走到这里说明运行环境被裁剪
            throw new IllegalStateException("SHA-256 not available", error);
        }
    }
}
