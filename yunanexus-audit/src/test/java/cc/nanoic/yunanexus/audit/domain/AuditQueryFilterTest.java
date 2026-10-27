package cc.nanoic.yunanexus.audit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索条件规范化。
 *
 * <p>守两件事：空串不能变成"查不到结果"的死条件；用户输入的通配符必须被当成字面量，
 * 否则一个 {@code %} 就是一次全表扫描。
 */
class AuditQueryFilterTest {

    @Test
    @DisplayName("空白串归一为 null，避免生成死条件")
    void blankBecomesNull() {
        AuditQueryFilter filter = new AuditQueryFilter()
                .setUsername("   ")
                .setModule("")
                .normalize();
        assertNull(filter.getUsername());
        assertNull(filter.getModule());
        assertTrue(filter.isEmpty());
    }

    @Test
    @DisplayName("首尾空格被去掉，值保留")
    void trimsValues() {
        assertEquals("alice", new AuditQueryFilter().setUsername("  alice ").normalize().getUsername());
    }

    @Test
    @DisplayName("关键字中的 LIKE 通配符被转义成字面量")
    void escapesLikeWildcards() {
        assertEquals("50\\%", new AuditQueryFilter().setKeyword("50%").normalize().getKeyword());
        assertEquals("a\\_b", new AuditQueryFilter().setKeyword("a_b").normalize().getKeyword());
        assertEquals("c\\\\d", new AuditQueryFilter().setKeyword("c\\d").normalize().getKeyword());
    }

    @Test
    @DisplayName("纯通配符关键字被转义后不再具有扫描能力")
    void bareWildcardIsNeutralized() {
        assertEquals("\\%", new AuditQueryFilter().setKeyword("%").normalize().getKeyword());
    }

    @Test
    @DisplayName("超长关键字被截断")
    void truncatesLongKeyword() {
        String keyword = "x".repeat(200);
        assertEquals(64, new AuditQueryFilter().setKeyword(keyword).normalize().getKeyword().length());
    }

    @Test
    @DisplayName("时间区间起止颠倒直接报错，而不是查出空结果")
    void rejectsInvertedRange() {
        AuditQueryFilter filter = new AuditQueryFilter()
                .setStart(LocalDateTime.of(2026, 10, 27, 12, 0))
                .setEnd(LocalDateTime.of(2026, 10, 26, 12, 0));
        assertThrows(IllegalArgumentException.class, filter::normalize);
    }

    @Test
    @DisplayName("带任一条件即不算空")
    void nonEmptyWhenAnyConditionSet() {
        assertFalse(new AuditQueryFilter().setStatus(0).normalize().isEmpty());
        assertFalse(new AuditQueryFilter().setUserId(7L).normalize().isEmpty());
    }
}
