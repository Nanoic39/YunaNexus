package cc.nanoic.yunanexus.auth.service.impl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CaptchaImageTest {
    @Test
    void rendersHeadlessPngWithNoFontDependency() throws Exception {
        String value=CaptchaImage.render("012789");
        assertTrue(value.startsWith("data:image/png;base64,"));
        var bytes=java.util.Base64.getDecoder().decode(value.substring(value.indexOf(',')+1));
        var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        assertEquals(204,image.getWidth());assertEquals(60,image.getHeight());
    }
}
