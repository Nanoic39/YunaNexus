package cc.nanoic.yunanexus.common.datascope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 数据范围条件 SQL 构建器。<p>
 *
 * 入参均为数值型 ID，直接内联无 SQL 注入风险（不接收任何字符串）。
 *
 * @author yunanexus
 */
public final class DataScopeSqlBuilder {

    private DataScopeSqlBuilder() {
    }

    /**
     * 构建数据范围条件。
     *
     * @param scope          数据范围（为 {@code null} 或 {@code all=true} 时不限制）
     * @param deptColumn     部门列名（含表别名）
     * @param createByColumn 创建人列名（含表别名）
     * @return 条件 SQL；返回 {@code null} 表示不加限制；无任何授权时返回 {@code "1 = 0"}
     */
    public static String build(DataScopeContext.Scope scope, String deptColumn, String createByColumn) {
        if (scope == null || scope.all()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String deptIds = joinDeptIds(scope.deptIds());
        if (!deptIds.isEmpty()) {
            parts.add(deptColumn + " IN (" + deptIds + ")");
        }
        if (scope.selfUserId() != null) {
            parts.add(createByColumn + " = " + scope.selfUserId());
        }
        if (parts.isEmpty()) {
            return "1 = 0";
        }
        return String.join(" OR ", parts);
    }

    private static String joinDeptIds(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return "";
        }
        return deptIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }
}
