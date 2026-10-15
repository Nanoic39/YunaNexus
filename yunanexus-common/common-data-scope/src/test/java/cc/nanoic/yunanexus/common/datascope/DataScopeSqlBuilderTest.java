package cc.nanoic.yunanexus.common.datascope;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link DataScopeSqlBuilder} 单元测试。
 *
 * @author yunanexus
 */
class DataScopeSqlBuilderTest {

    private static final String DEPT_COLUMN = "o.dept_id";
    private static final String CREATE_BY_COLUMN = "o.create_by";

    @Test
    void 上下文为空时不限制() {
        assertNull(DataScopeSqlBuilder.build(null, DEPT_COLUMN, CREATE_BY_COLUMN));
    }

    @Test
    void 全量可见时不限制() {
        assertNull(DataScopeSqlBuilder.build(DataScopeContext.Scope.unrestricted(), DEPT_COLUMN, CREATE_BY_COLUMN));
    }

    @Test
    void 仅部门范围() {
        String sql = DataScopeSqlBuilder.build(
                new DataScopeContext.Scope(false, List.of(1L, 2L), null), DEPT_COLUMN, CREATE_BY_COLUMN);
        assertEquals("o.dept_id IN (1, 2)", sql);
    }

    @Test
    void 仅本人范围() {
        String sql = DataScopeSqlBuilder.build(
                new DataScopeContext.Scope(false, List.of(), 100L), DEPT_COLUMN, CREATE_BY_COLUMN);
        assertEquals("o.create_by = 100", sql);
    }

    @Test
    void 部门与本人取并集() {
        String sql = DataScopeSqlBuilder.build(
                new DataScopeContext.Scope(false, List.of(1L), 100L), DEPT_COLUMN, CREATE_BY_COLUMN);
        assertEquals("o.dept_id IN (1) OR o.create_by = 100", sql);
    }

    @Test
    void 无任何授权时无可见数据() {
        String sql = DataScopeSqlBuilder.build(
                new DataScopeContext.Scope(false, List.of(), null), DEPT_COLUMN, CREATE_BY_COLUMN);
        assertEquals("1 = 0", sql);
    }

    @Test
    void 忽略空部门ID() {
        String sql = DataScopeSqlBuilder.build(
                new DataScopeContext.Scope(false, Arrays.asList(1L, null, 2L), null), DEPT_COLUMN, CREATE_BY_COLUMN);
        assertEquals("o.dept_id IN (1, 2)", sql);
    }
}
