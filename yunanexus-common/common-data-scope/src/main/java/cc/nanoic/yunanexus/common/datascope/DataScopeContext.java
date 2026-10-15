package cc.nanoic.yunanexus.common.datascope;

import java.util.List;
import java.util.function.Supplier;

/**
 * 数据范围上下文（ThreadLocal）。<p>
 * <b>本类不做取数</b>：由业务侧从权限中心（{@code GET /internal/permission/data-scope}）解析出
 * {@link Scope} 后，用 {@link #execute(Scope, Supplier)} 包裹受控查询。
 *
 * @author yunanexus
 */
public final class DataScopeContext {

    /**
     * 数据范围。<p>
     * {@code all=true} 表示不做限制；否则可见条件为
     * 「{@code deptId IN deptIds}」与「{@code createBy = selfUserId}」的并集；
     * 两者都为空表示无可见数据。
     *
     * @param all        是否全量可见
     * @param deptIds    可见部门 ID
     * @param selfUserId 非空时追加「仅本人」条件
     */
    public record Scope(boolean all, List<Long> deptIds, Long selfUserId) {

        /** 无任何授权（业务侧应返回空数据）。 */
        public static Scope none() {
            return new Scope(false, List.of(), null);
        }

        /** 全量可见。 */
        public static Scope unrestricted() {
            return new Scope(true, List.of(), null);
        }
    }

    private static final ThreadLocal<Scope> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /** 设置当前线程的数据范围。 */
    public static void set(Scope scope) {
        HOLDER.set(scope);
    }

    /** 读取当前线程的数据范围；未设置返回 {@code null}。 */
    public static Scope get() {
        return HOLDER.get();
    }

    /** 清理当前线程的数据范围。 */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 在指定数据范围下执行查询，结束后恢复原上下文（异常安全）。
     *
     * @param scope  数据范围
     * @param action 受控查询
     * @param <T>    返回类型
     * @return 查询结果
     */
    public static <T> T execute(Scope scope, Supplier<T> action) {
        Scope previous = HOLDER.get();
        HOLDER.set(scope);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
