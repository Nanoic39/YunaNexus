package cc.nanoic.yunanexus.auth.service.impl;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;

/** 无字体依赖的图形验证码，兼容 headless JRE。 */
public final class CaptchaImage {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final int[] DIGITS={0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f};
    private static final int[][] LINES={{2,2,20,2},{20,2,20,20},{20,20,20,38},
        {2,38,20,38},{2,20,2,38},{2,2,2,20},{2,20,20,20}};
    private CaptchaImage() {}
    public static String render(String code) {
        var image=new BufferedImage(204,60,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        try {
            graphics.setColor(new Color(245,248,252));graphics.fillRect(0,0,204,60);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(1));graphics.setColor(new Color(175,190,205));
            for(int i=0;i<8;i++) graphics.drawLine(RANDOM.nextInt(204),RANDOM.nextInt(60),RANDOM.nextInt(204),RANDOM.nextInt(60));
            var base=graphics.getTransform();
            for(int i=0;i<code.length();i++) {
                graphics.setTransform(base);graphics.translate(9+i*32,8+RANDOM.nextInt(6));
                graphics.rotate((RANDOM.nextDouble()-0.5)*0.24,11,20);
                graphics.setColor(new Color(25+RANDOM.nextInt(70),35+RANDOM.nextInt(60),65+RANDOM.nextInt(70)));
                graphics.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                int mask=DIGITS[code.charAt(i)-'0'];
                for(int j=0;j<7;j++) if((mask&(1<<j))!=0) {
                    int[] line=LINES[j];graphics.drawLine(line[0],line[1],line[2],line[3]);
                }
            }
            var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
            return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch(java.io.IOException e) {
            throw new IllegalStateException("验证码图片生成失败",e);
        } finally {graphics.dispose();}
    }
}
