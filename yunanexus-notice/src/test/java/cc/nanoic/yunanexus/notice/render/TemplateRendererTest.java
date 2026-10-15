package cc.nanoic.yunanexus.notice.render;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TemplateRenderer} 单元测试。
 *
 * @author yunanexus
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void rendersAllPlaceholders() {
        String result = renderer.render("订单 ${order} 已支付 ${amount} 元",
                Map.of("order", "A123", "amount", "99.00"));

        assertEquals("订单 A123 已支付 99.00 元", result);
    }

    @Test
    void rendersRepeatedPlaceholderEachTime() {
        assertEquals("X-X", renderer.render("${k}-${k}", Map.of("k", "X")));
    }

    @Test
    void insertsValueContainingDollarSignLiterally() {
        assertEquals("价 $5", renderer.render("价 ${price}", Map.of("price", "$5")));
    }

    @Test
    void returnsTemplateWithoutPlaceholdersUnchanged() {
        assertEquals("纯文本通知", renderer.render("纯文本通知", Map.of()));
    }

    @Test
    void returnsNullOrEmptyTemplateAsIs() {
        assertNull(renderer.render(null, Map.of()));
        assertEquals("", renderer.render("", Map.of()));
    }

    @Test
    void missingParamThrows() {
        BizException ex = assertThrows(BizException.class,
                () -> renderer.render("订单 ${order}", Map.of()));
        assertEquals(ErrorCode.NOTICE_PARAM_MISSING.getCode(), ex.getCode());
    }

    @Test
    void nullParamsWithPlaceholderThrows() {
        BizException ex = assertThrows(BizException.class,
                () -> renderer.render("${order}", null));
        assertEquals(ErrorCode.NOTICE_PARAM_MISSING.getCode(), ex.getCode());
    }
}
