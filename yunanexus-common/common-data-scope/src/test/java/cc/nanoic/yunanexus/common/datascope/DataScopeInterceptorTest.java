package cc.nanoic.yunanexus.common.datascope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataScopeInterceptor} SQL 改写单元测试。
 *
 * @author yunanexus
 */
class DataScopeInterceptorTest {

    private final DataScopeInterceptor interceptor = new DataScopeInterceptor();

    @Test
    void 无WHERE时注入条件() {
        String out = interceptor.rewrite("SELECT * FROM orders o", "o.dept_id IN (1, 2)");

        assertNotNull(out);
        assertTrue(out.contains("WHERE"), out);
        assertTrue(out.contains("o.dept_id IN (1, 2)"), out);
    }

    @Test
    void 已有WHERE时AND注入且两侧加括号() {
        String out = interceptor.rewrite(
                "SELECT * FROM orders o WHERE o.status = 1 OR o.status = 2", "o.dept_id IN (1)");

        assertNotNull(out);
        assertTrue(out.contains("o.status = 1"), out);
        assertTrue(out.contains("o.dept_id IN (1)"), out);
        // 原 WHERE 与数据范围条件均被括号包裹，避免 OR 优先级歧义
        assertTrue(out.contains(") AND ("), out);
    }

    @Test
    void 非简单SELECT跳过注入() {
        assertNull(interceptor.rewrite("SELECT 1 UNION SELECT 2", "1 = 1"));
    }

    @Test
    void SQL解析失败跳过注入() {
        assertNull(interceptor.rewrite("NOT A VALID SQL", "1 = 1"));
    }
}
