package cc.nanoic.yunanexus.common.audit.collect;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求级审计上下文：让业务代码在不感知采集链路的前提下补充"发生了什么"。<p>
 *
 * 用请求属性而不是 ThreadLocal：Servlet 异步与线程池切换时 ThreadLocal 会丢，
 * 而请求属性跟着这一次请求走。这里用 {@link java.util.concurrent.ConcurrentHashMap}
 * 承载，key 为 requestId，由拦截器在 preHandle 建立、afterCompletion 清理。
 *
 * @author yunanexus
 */
public final class AuditCollectContext {

    /** 单次请求最多保留的字段数，防止业务误写把内存撑大 */
    private static final int MAX_FIELDS = 32;

    private static final ThreadLocal<Map<String, String>> FIELDS =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private static final ThreadLocal<String> RESULT = new ThreadLocal<>();

    private AuditCollectContext() {
    }

    /**
     * 记录一个业务字段（如 {@code targetUserId=7}）。
     *
     * @param key   字段名
     * @param value 字段值，null 会被忽略
     */
    public static void put(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        Map<String, String> fields = FIELDS.get();
        if (fields.size() >= MAX_FIELDS) {
            return;
        }
        fields.put(key, String.valueOf(value));
    }

    /**
     * 记录执行结果摘要（如"已将用户 7 的角色更新为 ROLE_ADMIN"）。
     *
     * @param summary 结果摘要
     */
    public static void result(String summary) {
        if (summary != null) {
            RESULT.set(summary);
        }
    }

    /**
     * 取当前请求累积的字段。
     *
     * @return 字段快照（只读副本）
     */
    public static Map<String, String> fields() {
        return new LinkedHashMap<>(FIELDS.get());
    }

    /**
     * 取结果摘要。
     *
     * @return 结果摘要；未设置时为 null
     */
    public static String result() {
        return RESULT.get();
    }

    /** 请求结束时清理，避免线程复用串数据 */
    public static void clear() {
        FIELDS.remove();
        RESULT.remove();
    }
}
