package cc.nanoic.yunanexus.meta.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SegmentBuffer} 单元测试。<p>
 *
 * 覆盖：顺序分配、耗尽、剩余量/容量/使用率、非法区间、步长与加载时间透传。
 *
 * @author yunanexus
 */
class SegmentBufferTest {

    @Test
    void 非法区间应拒绝() {
        assertThrows(IllegalArgumentException.class,
                () -> new SegmentBuffer(10, 5, 5, 0L));
    }

    @Test
    void 顺序分配() {
        SegmentBuffer buffer = new SegmentBuffer(1, 5, 5, 100L);
        assertEquals(1L, buffer.nextId());
        assertEquals(2L, buffer.nextId());
        assertEquals(3L, buffer.nextId());
        assertEquals(4L, buffer.nextId());
        assertFalse(buffer.isExhausted());
        assertEquals(5L, buffer.nextId());
        assertTrue(buffer.isExhausted());
    }

    @Test
    void 耗尽后返回负一() {
        SegmentBuffer buffer = new SegmentBuffer(1, 2, 2, 100L);
        assertEquals(1L, buffer.nextId());
        assertEquals(2L, buffer.nextId());
        assertTrue(buffer.isExhausted());
        assertEquals(-1L, buffer.nextId());
    }

    @Test
    void 剩余量随分配递减且不为负() {
        SegmentBuffer buffer = new SegmentBuffer(1, 10, 10, 100L);
        assertEquals(10L, buffer.remaining());
        buffer.nextId();
        buffer.nextId();
        assertEquals(8L, buffer.remaining());
        for (int i = 0; i < 10; i++) {
            buffer.nextId();
        }
        assertEquals(0L, buffer.remaining());
    }

    @Test
    void 容量与使用率() {
        SegmentBuffer buffer = new SegmentBuffer(1, 10, 10, 100L);
        assertEquals(10L, buffer.capacity());
        assertEquals(0.0, buffer.usedRatio(), 1e-9);
        for (int i = 0; i < 8; i++) {
            buffer.nextId();
        }
        assertEquals(0.8, buffer.usedRatio(), 1e-9);
    }

    @Test
    void 步长与加载时间透传() {
        SegmentBuffer buffer = new SegmentBuffer(1, 100, 100, 123456789L);
        assertEquals(100L, buffer.getStep());
        assertEquals(123456789L, buffer.getLoadTime());
    }
}