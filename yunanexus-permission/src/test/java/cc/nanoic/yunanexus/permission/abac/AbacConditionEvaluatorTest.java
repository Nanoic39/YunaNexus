package cc.nanoic.yunanexus.permission.abac;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbacConditionEvaluator} 单元测试。
 *
 * @author yunanexus
 */
class AbacConditionEvaluatorTest {

    private AbacConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AbacConditionEvaluator(new ObjectMapper());
    }

    @Test
    void 空条件校验与评估均通过() {
        assertDoesNotThrow(() -> evaluator.validate(null));
        assertDoesNotThrow(() -> evaluator.validate("  "));
        assertTrue(evaluator.evaluate(null, Map.of()));
        assertTrue(evaluator.evaluate("", null));
    }

    @Test
    void 非法JSON或非数组抛出业务异常() {
        assertThrows(BizException.class, () -> evaluator.validate("{not-json"));
        assertThrows(BizException.class, () -> evaluator.validate("{\"attribute\":\"ip\"}"));
    }

    @Test
    void 缺少必填字段或不支持的operator抛出业务异常() {
        assertThrows(BizException.class, () -> evaluator.validate("[{\"operator\":\"EQ\"}]"));
        assertThrows(BizException.class, () -> evaluator.validate("[{\"attribute\":\"ip\"}]"));
        assertThrows(BizException.class,
                () -> evaluator.validate("[{\"attribute\":\"ip\",\"operator\":\"REGEX\"}]"));
    }

    @Test
    void 合法条件通过校验() {
        assertDoesNotThrow(() -> evaluator.validate(
                "[{\"attribute\":\"ip\",\"operator\":\"EQ\",\"value\":\"10.0.0.1\"}]"));
    }

    @Test
    void EQ与NE按字符串比较() {
        String conditions = "[{\"attribute\":\"ip\",\"operator\":\"EQ\",\"value\":\"10.0.0.1\"}]";
        assertTrue(evaluator.evaluate(conditions, Map.of("ip", "10.0.0.1")));
        assertFalse(evaluator.evaluate(conditions, Map.of("ip", "10.0.0.2")));
        assertFalse(evaluator.evaluate(conditions, Map.of()));

        String ne = "[{\"attribute\":\"ip\",\"operator\":\"NE\",\"value\":\"10.0.0.1\"}]";
        assertFalse(evaluator.evaluate(ne, Map.of("ip", "10.0.0.1")));
        assertTrue(evaluator.evaluate(ne, Map.of("ip", "10.0.0.2")));
    }

    @Test
    void IN与NOT_IN匹配数组元素() {
        String in = "[{\"attribute\":\"dept\",\"operator\":\"IN\",\"value\":[\"a\",\"b\"]}]";
        assertTrue(evaluator.evaluate(in, Map.of("dept", "a")));
        assertFalse(evaluator.evaluate(in, Map.of("dept", "c")));

        String notIn = "[{\"attribute\":\"dept\",\"operator\":\"NOT_IN\",\"value\":[\"a\",\"b\"]}]";
        assertFalse(evaluator.evaluate(notIn, Map.of("dept", "a")));
        assertTrue(evaluator.evaluate(notIn, Map.of("dept", "c")));
    }

    @Test
    void EXISTS与NOT_EXISTS判断属性是否存在() {
        assertTrue(evaluator.evaluate("[{\"attribute\":\"ip\",\"operator\":\"EXISTS\"}]", Map.of("ip", "x")));
        assertFalse(evaluator.evaluate("[{\"attribute\":\"ip\",\"operator\":\"EXISTS\"}]", Map.of()));
        assertTrue(evaluator.evaluate("[{\"attribute\":\"ip\",\"operator\":\"NOT_EXISTS\"}]", Map.of()));
    }

    @Test
    void 多条件以AND组合() {
        String conditions = "["
                + "{\"attribute\":\"ip\",\"operator\":\"EQ\",\"value\":\"10.0.0.1\"},"
                + "{\"attribute\":\"dept\",\"operator\":\"IN\",\"value\":[\"a\"]}]";
        assertTrue(evaluator.evaluate(conditions, Map.of("ip", "10.0.0.1", "dept", "a")));
        assertFalse(evaluator.evaluate(conditions, Map.of("ip", "10.0.0.1", "dept", "b")));
    }
}
