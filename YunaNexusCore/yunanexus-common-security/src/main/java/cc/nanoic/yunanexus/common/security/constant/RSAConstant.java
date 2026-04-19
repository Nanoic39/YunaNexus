package cc.nanoic.yunanexus.common.security.constant;

import org.springframework.beans.factory.annotation.Value;

/**
 * RSA常量定义
 */
public final class RSAConstant {

    private RSAConstant() {
    }

    /**
     * RSA算法
     */
    @Value("${yunanexus.security.rsa.algorithm}")
    public static final String ALGORITHM = "RSA";

    /**
     * RSA填充模式
     */
    @Value("${yunanexus.security.rsa.transformation}")
    public static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    /**
     * 密钥位数
     */
    @Value("${yunanexus.security.rsa.key-size}")
    public static final int KEY_SIZE = 2048;

    /**
     * 密钥文件根路径
     */
    @Value("${yunanexus.security.rsa.secret-dir}")
    public static final String SECRET_DIR = "./rsa/";

    /**
     * 公钥文件路劲
     */
    @Value("${yunanexus.security.rsa.public-key-filename}")
    public static final String PUBLIC_KEY_FILENAME = "rsa.pub";

    /**
     * 私钥文件路径
     */
    @Value("${yunanexus.security.rsa.private-key-filename}")
    public static final String PRIVATE_KEY_FILENAME = "rsa";

    /**
     * 是否自动生成密钥对
     */
    @Value("${yunanexus.security.rsa.auto-generate}")
    public static final Boolean AUTO_GENERATE = true;
}
